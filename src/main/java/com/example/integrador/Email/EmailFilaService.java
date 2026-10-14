package com.example.integrador.Email;

import com.example.integrador.Config.DashboardClockConfig;
import com.example.integrador.Enum.TipoPessoa;
import com.example.integrador.Repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.UUID;

@Service
public class EmailFilaService {
    private final EmailRepository repository;
    private final UsuarioRepository usuarios;
    private final EmailDisponibilidadeService disponibilidade;
    private final EmailGateway gateway;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public EmailFilaService(EmailRepository repository, UsuarioRepository usuarios,
            EmailDisponibilidadeService disponibilidade, EmailGateway gateway,
            PlatformTransactionManager manager, Clock clock) {
        this.repository = repository;
        this.usuarios = usuarios;
        this.disponibilidade = disponibilidade;
        this.gateway = gateway;
        this.transaction = new TransactionTemplate(manager);
        this.clock = clock;
    }

    public void processarUma() {
        if (!disponibilidade.impedimentos().isEmpty()) return;
        Reserva reserva = transaction.execute(status -> reservar());
        if (reserva == null) return;
        // Revalida imediatamente antes do envio, em outra transação curta.
        boolean permitido = Boolean.TRUE.equals(transaction.execute(status -> elegivel(reserva.usuarioId(), reserva.email())));
        if (!permitido) {
            concluir(reserva, EmailDto.Estado.SUPRIMIDA, "CONTA_OU_EMAIL_ALTERADO", false);
            return;
        }
        try {
            gateway.enviar(reserva.id(), reserva.email(), reserva.conteudo()); // Fora de transação e locks.
            concluir(reserva, EmailDto.Estado.ACEITA_SMTP, null, false);
        } catch (EmailEnvioException exception) {
            concluir(reserva, exception.isIncerto() ? EmailDto.Estado.INCERTA : EmailDto.Estado.FALHA,
                    exception.getMessage(), exception.isRepetivel());
        } catch (RuntimeException exception) {
            concluir(reserva, EmailDto.Estado.INCERTA, "RESULTADO_SMTP_INCERTO", false);
        }
    }

    private Reserva reservar() {
        var config = repository.bloquearConfiguracao();
        Instant agora = clock.instant();
        LocalDate hoje = LocalDate.ofInstant(agora, DashboardClockConfig.ZONA);
        LocalTime hora = LocalTime.ofInstant(agora, DashboardClockConfig.ZONA);
        repository.expirar(hoje, !hora.isBefore(LocalTime.of(18, 0)));
        repository.recuperarReservas(agora.minusSeconds(600));
        if (config.getUltimaReserva() != null && agora.isBefore(config.getUltimaReserva().plusSeconds(1))) return null;
        var pendentes = repository.pendentes(agora);
        if (pendentes.isEmpty()) return null;
        var entrega = pendentes.getFirst();
        config.setUltimaReserva(agora);
        if (!elegivel(entrega.getUsuarioId(), entrega.getEmail())) {
            entrega.setEstado(EmailDto.Estado.SUPRIMIDA);
            entrega.setErro("CONTA_OU_EMAIL_ALTERADO");
            return null;
        }
        entrega.setEstado(EmailDto.Estado.PROCESSANDO);
        entrega.setTentativas(entrega.getTentativas() + 1);
        entrega.setReservadoEm(agora);
        entrega.setReservaId(UUID.randomUUID());
        var lote = entrega.getLote();
        return new Reserva(entrega.getId(), entrega.getReservaId(), entrega.getUsuarioId(), entrega.getEmail(),
                new EmailDto.Conteudo(lote.getAssunto(), lote.getTexto(), lote.getHtml()));
    }

    private boolean elegivel(Long usuarioId, String email) {
        return disponibilidade.impedimentos().isEmpty() && disponibilidade.permitido(email)
                && usuarios.findById(usuarioId).filter(u -> u.isEnabled()
                && (u.getTipoPessoa() == TipoPessoa.ALUNO || u.getTipoPessoa() == TipoPessoa.MOTORISTA)
                && email.equals(EmailDisponibilidadeService.normalizar(u.getEmail()))).isPresent();
    }

    private void concluir(Reserva reserva, EmailDto.Estado estado, String erro, boolean repetir) {
        transaction.executeWithoutResult(status -> {
            repository.bloquearConfiguracao();
            EmailEntrega entrega = repository.entrega(reserva.id());
            if (entrega == null || entrega.getEstado() != EmailDto.Estado.PROCESSANDO
                    || !reserva.reservaId().equals(entrega.getReservaId())) return;
            if (repetir && entrega.getTentativas() < 4) {
                long minutos = switch (entrega.getTentativas()) { case 1 -> 1; case 2 -> 5; default -> 15; };
                entrega.setEstado(EmailDto.Estado.PENDENTE);
                entrega.setProximaTentativa(clock.instant().plusSeconds(minutos * 60));
            } else {
                entrega.setEstado(estado);
                if (estado == EmailDto.Estado.ACEITA_SMTP) entrega.setAceitoEm(clock.instant());
            }
            entrega.setErro(erro);
        });
    }
    private record Reserva(UUID id, UUID reservaId, Long usuarioId, String email, EmailDto.Conteudo conteudo) {}
}
