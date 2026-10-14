package com.example.integrador.Email;

import com.example.integrador.Config.DashboardClockConfig;
import com.example.integrador.Exception.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

@Service
public class EmailLoteService {
    private final EmailLoteRepository lotes;
    private final EmailRepository repository;
    private final ProgramacaoEmailService programacao;
    private final EmailDisponibilidadeService disponibilidade;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public EmailLoteService(EmailLoteRepository lotes, EmailRepository repository, ProgramacaoEmailService programacao,
            EmailDisponibilidadeService disponibilidade, PlatformTransactionManager manager, Clock clock) {
        this.lotes = lotes;
        this.repository = repository;
        this.programacao = programacao;
        this.disponibilidade = disponibilidade;
        this.transaction = new TransactionTemplate(manager);
        this.clock = clock;
    }

    @PreAuthorize("hasRole('ADMIN')")
    public EmailDto.Previa previa() { return programacao.preparar().previa(); }

    @PreAuthorize("hasRole('ADMIN')")
    public EmailDto.LoteResponse solicitar(EmailDto.EnvioRequest request) {
        if (request.requestId() == null) throw new RegraNegocioException("requestId é obrigatório.");
        String motivo = motivo(request.reenviar(), request.motivo());
        Long adminId = usuarioAtual();
        return criar(request.requestId(), EmailDto.Origem.MANUAL, request.reenviar(), motivo, adminId);
    }

    /** Rotina interna: não cria nem armazena token de administrador. */
    public void solicitarAutomatico() {
        boolean automatico = Boolean.TRUE.equals(transaction.execute(status -> repository.configuracao().isAutomatico()));
        if (!automatico) return;
        LocalDate dia = hoje();
        UUID id = UUID.nameUUIDFromBytes(("email-programacao-" + dia).getBytes(StandardCharsets.UTF_8));
        criar(id, EmailDto.Origem.AUTOMATICA, false, null, null);
    }

    private EmailDto.LoteResponse criar(UUID id, EmailDto.Origem origem, boolean reenvio, String motivo, Long admin) {
        var anterior = transaction.execute(status -> lotes.findById(id).map(l -> {
            conferirParametros(l, origem, reenvio, motivo);
            return resposta(l, true);
        }).orElse(null));
        if (anterior != null) return anterior;
        disponibilidade.exigirDisponivel();
        var preparacao = programacao.preparar(); // Somente banco local, nunca SMTP.
        if (origem == EmailDto.Origem.MANUAL && preparacao.previa().viagens().isEmpty())
            throw new ConflitoException("Não há programação para hoje. Nenhum e-mail foi enfileirado.");
        if (origem == EmailDto.Origem.MANUAL && preparacao.destinatarios().isEmpty())
            throw new ConflitoException("Não há destinatários ativos com e-mail permitido.");
        return transaction.execute(status -> {
            var config = repository.bloquearConfiguracao();
            var existente = lotes.findById(id);
            if (existente.isPresent()) {
                conferirParametros(existente.get(), origem, reenvio, motivo);
                return resposta(existente.get(), true);
            }
            if (!preparacao.previa().dataReferencia().equals(hoje()))
                throw new ConflitoException("O dia mudou durante a preparação. Atualize a prévia e tente novamente.");
            if (origem == EmailDto.Origem.AUTOMATICA) {
                LocalTime hora = LocalTime.ofInstant(clock.instant(), DashboardClockConfig.ZONA);
                if (!config.isAutomatico() || hora.isBefore(LocalTime.of(15, 0)) || !hora.isBefore(LocalTime.of(18, 0))
                        || repository.existeAutomatico(hoje())) return null;
                // Permite recuperar mais tarde, se a administração cadastrar a grade até antes das 18h.
                if (preparacao.previa().viagens().isEmpty() || preparacao.destinatarios().isEmpty()) return null;
            }
            EmailLote lote = new EmailLote();
            lote.setId(id);
            lote.setDataReferencia(hoje());
            lote.setDiaAutomatico(origem == EmailDto.Origem.AUTOMATICA ? hoje() : null);
            lote.setOrigem(origem);
            lote.setReenvio(reenvio);
            lote.setMotivo(motivo);
            lote.setCriadoEm(clock.instant());
            lote.setSolicitanteId(admin);
            lote.setIgnorados((int) preparacao.previa().destinatariosIgnorados());
            lote.setAssunto(preparacao.previa().mensagem().assunto());
            lote.setTexto(preparacao.previa().mensagem().texto());
            lote.setHtml(preparacao.previa().mensagem().html());
            repository.persistir(lote);
            for (var destinatario : preparacao.destinatarios()) {
                Integer revisao = repository.ultimaRevisao(hoje(), destinatario.email());
                if (revisao != null && !reenvio) {
                    lote.setIgnorados(lote.getIgnorados() + 1);
                    continue;
                }
                EmailEntrega entrega = new EmailEntrega();
                entrega.setId(UUID.randomUUID());
                entrega.setLote(lote);
                entrega.setUsuarioId(destinatario.usuarioId());
                entrega.setEmail(destinatario.email());
                entrega.setDataReferencia(hoje());
                entrega.setRevisao(revisao == null ? 0 : revisao + 1);
                entrega.setProximaTentativa(clock.instant());
                repository.persistir(entrega);
            }
            return resposta(lote, true);
        });
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public EmailDto.LoteResponse detalhe(UUID id) {
        return resposta(lotes.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("Lote não encontrado.")), true);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public EmailDto.Historico historico(int pagina, int tamanho) {
        if (pagina < 0 || tamanho < 1 || tamanho > 100) throw new RegraNegocioException("Página >= 0 e tamanho entre 1 e 100.");
        var resultado = lotes.findAll(PageRequest.of(pagina, tamanho, Sort.by(Sort.Order.desc("criadoEm"), Sort.Order.desc("id"))));
        return new EmailDto.Historico(pagina, tamanho, resultado.getTotalElements(), resultado.getTotalPages(),
                resultado.stream().map(l -> resposta(l, false)).toList());
    }

    private EmailDto.LoteResponse resposta(EmailLote lote, boolean detalhar) {
        Map<EmailDto.Estado, Long> totais = new EnumMap<>(EmailDto.Estado.class);
        for (var estado : EmailDto.Estado.values()) totais.put(estado, 0L);
        repository.totais(lote.getId()).forEach(row -> totais.put((EmailDto.Estado) row[0], ((Number) row[1]).longValue()));
        List<EmailDto.EntregaResponse> entregas = detalhar ? repository.entregas(lote.getId()).stream()
                .map(e -> new EmailDto.EntregaResponse(e.getId(), e.getUsuarioId(), e.getEmail(), e.getRevisao(),
                        e.getEstado(), e.getTentativas(), e.getErro(), e.getAceitoEm())).toList() : List.of();
        return new EmailDto.LoteResponse(lote.getId(), lote.getDataReferencia(), lote.getOrigem(), lote.isReenvio(),
                lote.getMotivo(), lote.getCriadoEm(), lote.getIgnorados(), totais, entregas,
                detalhar ? new EmailDto.Conteudo(lote.getAssunto(), lote.getTexto(), lote.getHtml()) : null);
    }

    private String motivo(boolean reenvio, String valor) {
        String motivo = valor == null ? null : valor.strip();
        if (reenvio && (motivo == null || motivo.isBlank())) throw new RegraNegocioException("Informe o motivo do reenvio.");
        if (motivo != null && motivo.length() > 500) throw new RegraNegocioException("Motivo deve ter até 500 caracteres.");
        if (!reenvio && motivo != null && !motivo.isBlank()) throw new RegraNegocioException("Motivo é permitido somente no reenvio.");
        return reenvio ? motivo : null;
    }
    private void conferirParametros(EmailLote lote, EmailDto.Origem origem, boolean reenvio, String motivo) {
        if (lote.getOrigem() != origem || lote.isReenvio() != reenvio || !Objects.equals(lote.getMotivo(), motivo))
            throw new ConflitoException("requestId já foi utilizado com outros parâmetros.");
    }
    private LocalDate hoje() { return LocalDate.ofInstant(clock.instant(), DashboardClockConfig.ZONA); }
    private Long usuarioAtual() {
        if (!(SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken jwt))
            throw new AuthenticationCredentialsNotFoundException("JWT obrigatório.");
        return Long.valueOf(jwt.getToken().getSubject());
    }
}
