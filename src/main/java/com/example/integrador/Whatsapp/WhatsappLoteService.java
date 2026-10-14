package com.example.integrador.Whatsapp;

import com.example.integrador.Exception.*;
import com.example.integrador.Model.Usuario;
import com.example.integrador.Whatsapp.WhatsappDto.*;
import com.example.integrador.Whatsapp.WhatsappPreparacaoService.Preparado;
import com.example.integrador.Whatsapp.WhatsappTipos.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
import java.time.*;
import java.util.*;

@Service
public class WhatsappLoteService {
    private final WhatsappRepos repos;
    private final WhatsappLoteRepository lotes;
    private final WhatsappPreparacaoService preparacao;
    private final ProgramacaoPdfService pdf;
    private final WhatsappDisponibilidadeService disponibilidade;
    private final WhatsappIdentidade identidade;
    private final ProgramacaoWhatsappService programacoes;
    private final TransactionTemplate tx;
    private final JsonMapper json;
    private final Clock clock;

    public WhatsappLoteService(WhatsappRepos repos, WhatsappLoteRepository lotes, WhatsappPreparacaoService preparacao,
                              ProgramacaoPdfService pdf, WhatsappDisponibilidadeService disponibilidade, WhatsappIdentidade identidade,
                              ProgramacaoWhatsappService programacoes, TransactionTemplate tx, JsonMapper json, Clock clock) {
        this.repos = repos; this.lotes = lotes; this.preparacao = preparacao; this.pdf = pdf; this.disponibilidade = disponibilidade;
        this.identidade = identidade; this.programacoes = programacoes; this.tx = tx; this.json = json; this.clock = clock;
    }
    @PreAuthorize("hasRole('ADMIN')")
    public LoteResponse solicitarManual(EnvioRequest request) {
        if (request.reenviar() && (request.motivo() == null || request.motivo().isBlank())) throw new RegraNegocioException("Informe o motivo do reenvio");
        Long solicitante = tx.execute(s -> identidade.atual().getId());
        var existente = tx.execute(s -> repos.buscar(WhatsappLote.class, request.requestId()));
        if (existente != null) {
            if (existente.getOrigem() != Origem.MANUAL || existente.getPublico() != request.publico()
                    || existente.isReenvio() != request.reenviar() || !Objects.equals(existente.getMotivo(), motivo(request.motivo()))) {
                throw new ConflitoException("requestId já utilizado com parâmetros diferentes");
            }
            return detalhar(request.requestId());
        }
        disponibilidade.verificarEnvio();
        criar(request.requestId(), Origem.MANUAL, request.publico(), request.reenviar(), motivo(request.motivo()), solicitante);
        return detalhar(request.requestId());
    }

    /** Operação interna, sem JWT de ADMIN; o scheduler verifica a configuração global. */
    public void solicitarAutomatico() {
        LocalDate data = programacoes.hoje();
        if (Boolean.TRUE.equals(tx.execute(s -> repos.temAutomatico(data)))) return;
        disponibilidade.verificarEnvio();
        criar(UUID.randomUUID(), Origem.AUTOMATICA, Publico.TODOS, false, null, null);
    }
    private String motivo(String valor) { return valor == null || valor.isBlank() ? null : valor.trim(); }
    private void criar(UUID id, Origem origem, Publico publico, boolean reenviar, String motivo, Long solicitante) {
        var dados = preparacao.preparar(publico);
        Map<Long, byte[]> documentos = new HashMap<>();
        for (var item : dados) if (item.impedimento() == null) documentos.put(item.usuarioId(), pdf.gerar(item.programacao()));
        tx.executeWithoutResult(s -> {
            var configuracao = repos.bloquearConfiguracao();
            LocalDate data = programacoes.hoje();
            var existente = repos.buscar(WhatsappLote.class, id);
            if (existente != null) {
                if (existente.getOrigem() != origem || existente.getPublico() != publico || existente.isReenvio() != reenviar
                        || !Objects.equals(existente.getMotivo(), motivo)) throw new ConflitoException("requestId já utilizado com parâmetros diferentes");
                return;
            }
            if (origem == Origem.AUTOMATICA) {
                var hora = java.time.LocalTime.ofInstant(clock.instant(), com.example.integrador.Config.DashboardClockConfig.ZONA);
                if (!configuracao.isAutomatico() || hora.isBefore(java.time.LocalTime.of(15, 0))
                        || !hora.isBefore(java.time.LocalTime.of(18, 0)) || repos.temAutomatico(data)) return;
            }
            var lote = new WhatsappLote(); lote.setId(id); lote.setOrigem(origem); lote.setPublico(publico);
            lote.setDataReferencia(data); lote.setReenvio(reenviar); lote.setMotivo(motivo); lote.setCriadoEm(clock.instant());
            if (origem == Origem.AUTOMATICA) lote.setDiaAutomatico(data);
            if (solicitante != null) lote.setSolicitante(repos.buscar(Usuario.class, solicitante));
            repos.persistir(lote);
            for (var item : dados) {
                if (!data.equals(item.programacao().dataReferencia())) throw new RegraNegocioException("A data mudou durante a preparação. Atualize a prévia.");
                var anteriores = repos.anteriores(data, item.usuarioId());
                // Uma supressão ainda sem PDF não consome o envio normal: permite cadastro corrigido no mesmo dia.
                boolean jaPreparado = anteriores.stream().anyMatch(e -> e.getPdf() != null);
                if (!reenviar && jaPreparado) { lote.setIgnoradosDuplicidade(lote.getIgnoradosDuplicidade() + 1); continue; }
                int revisao = anteriores.isEmpty() ? 0 : anteriores.getFirst().getRevisao() + 1;
                var entrega = new WhatsappEntrega(); entrega.setId(UUID.randomUUID()); entrega.setLote(lote);
                entrega.setUsuario(repos.buscar(Usuario.class, item.usuarioId())); entrega.setDestinatarioOriginalId(item.usuarioId());
                entrega.setNomeDestinatario(item.nome()); entrega.setNumero(item.numero()); entrega.setAssinaturaRevisao(item.assinaturaRevisao());
                entrega.setDataReferencia(data); entrega.setRevisao(revisao); entrega.setReferencia(item.programacao().referencia());
                entrega.setProgramacaoJson(json.writeValueAsString(item.programacao())); entrega.setPdf(documentos.get(item.usuarioId()));
                entrega.setStatus(item.impedimento() == null ? StatusEntrega.PENDENTE : StatusEntrega.SUPRIMIDA);
                entrega.setErro(item.impedimento()); entrega.setCriadoEm(clock.instant()); entrega.setAtualizadoEm(clock.instant());
                repos.persistir(entrega);
            }
        });
    }
    @PreAuthorize("hasRole('ADMIN')")
    public PreviaResponse previa(Long usuarioId) { return preparacao.previa(usuarioId); }

    @PreAuthorize("hasRole('ADMIN')")
    public HistoricoResponse historico(int pagina, int tamanho) {
        if (pagina < 0 || tamanho < 1 || tamanho > 100) throw new RegraNegocioException("Página deve ser >= 0 e tamanho entre 1 e 100");
        var resultado = lotes.findAll(PageRequest.of(pagina, tamanho, Sort.by(Sort.Direction.DESC, "criadoEm").and(Sort.by("id"))));
        return new HistoricoResponse(resultado.stream().map(l -> new LoteResumo(l.getId(), l.getDataReferencia(), l.getOrigem(),
                l.getPublico(), l.isReenvio(), l.getCriadoEm())).toList(), pagina, tamanho, resultado.getTotalElements(), resultado.getTotalPages());
    }
    @PreAuthorize("hasRole('ADMIN')")
    public LoteResponse detalhar(UUID id) {
        return tx.execute(s -> {
            var lote = repos.buscar(WhatsappLote.class, id);
            if (lote == null) throw new RecursoNaoEncontradoException("Lote não encontrado");
            var entregas = repos.entregas(id);
            Map<StatusEntrega, Long> totais = new EnumMap<>(StatusEntrega.class);
            for (StatusEntrega estado : StatusEntrega.values()) totais.put(estado, 0L);
            entregas.forEach(e -> totais.merge(e.getStatus(), 1L, Long::sum));
            return new LoteResponse(lote.getId(), lote.getDataReferencia(), lote.getOrigem(), lote.getPublico(), lote.isReenvio(),
                    lote.getMotivo(), lote.getCriadoEm(), lote.getIgnoradosDuplicidade(), totais,
                    entregas.stream().map(e -> new EntregaResponse(e.getId(), e.getDestinatarioOriginalId(), e.getNomeDestinatario(),
                        WhatsappSeguranca.mascarar(e.getNumero()), e.getRevisao(), e.getStatus(), e.getTentativas(), e.getErro(), e.getAtualizadoEm())).toList());
        });
    }
}
