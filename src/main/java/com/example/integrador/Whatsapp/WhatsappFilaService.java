package com.example.integrador.Whatsapp;

import com.example.integrador.Config.DashboardClockConfig;
import com.example.integrador.Whatsapp.WhatsappTipos.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Outbox persistida: reserva curta e comunicação HTTP fora de qualquer transação. */
@Service
public class WhatsappFilaService {
    private final WhatsappRepos repos;
    private final WhatsappDisponibilidadeService disponibilidade;
    private final MetaWhatsappGateway meta;
    private final TransactionTemplate tx;
    private final Clock clock;
    public WhatsappFilaService(WhatsappRepos repos, WhatsappDisponibilidadeService disponibilidade, MetaWhatsappGateway meta,
                               TransactionTemplate tx, Clock clock) {
        this.repos = repos; this.disponibilidade = disponibilidade; this.meta = meta; this.tx = tx; this.clock = clock;
    }
    public record Reserva(UUID entregaId, UUID token, String numero, String nome, LocalDate data, String referencia, byte[] pdf) {}

    public void processarProxima() {
        Reserva reserva = reservar();
        if (reserva == null) return;
        try {
            String media = meta.uploadPdf(reserva.pdf(), "programacao-" + reserva.data() + ".pdf");
            // Upload pode levar segundos: conferir cancelamento, número e conta novamente antes da mensagem.
            if (!aindaElegivel(reserva)) { suprimir(reserva); return; }
            String mensagem = meta.enviarProgramacao(reserva.numero(), reserva.nome(),
                    reserva.data().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")), reserva.referencia(), media, reserva.entregaId().toString());
            concluir(reserva, mensagem, null);
        } catch (MetaWhatsappException exception) { concluir(reserva, null, exception); }
        catch (RuntimeException exception) {
            // Falha não classificada pode ter acontecido depois do aceite. Nunca repetir às cegas.
            concluir(reserva, null, new MetaWhatsappException("PROCESSAMENTO_INDETERMINADO", true, false));
        }
    }
    Reserva reservar() {
        return tx.execute(s -> {
            var config = repos.bloquearConfiguracao();
            Instant agora = clock.instant();
            for (var antiga : repos.reservasAntigas(agora.minus(Duration.ofMinutes(10)))) {
                antiga.setStatus(StatusEntrega.INCERTA); antiga.setErro("RESERVA_INTERROMPIDA"); antiga.setAtualizadoEm(agora); antiga.setReserva(null);
            }
            for (UUID id : repos.expiradasCandidatas(hoje(), !agora.isBefore(hoje().atTime(18, 0).atZone(DashboardClockConfig.ZONA).toInstant()))) {
                var entrega = repos.buscar(WhatsappEntrega.class, id); entrega.setStatus(StatusEntrega.EXPIRADA);
                entrega.setErro("JANELA_DE_ENVIO_ENCERRADA"); entrega.setAtualizadoEm(agora);
            }
            if (!disponibilidade.envioConfigurado() || config.getUltimaReserva() != null && config.getUltimaReserva().plusSeconds(1).isAfter(agora)) return null;
            for (var entrega : repos.pendentes(agora)) {
                if (!elegivel(entrega)) {
                    entrega.setStatus(StatusEntrega.SUPRIMIDA); entrega.setErro("CONTA_ASSINATURA_OU_NUMERO_ALTERADO"); entrega.setAtualizadoEm(agora); continue;
                }
                config.setUltimaReserva(agora);
                entrega.setStatus(StatusEntrega.PROCESSANDO); entrega.setProcessamentoEm(agora); entrega.setReserva(UUID.randomUUID());
                entrega.setTentativas(entrega.getTentativas() + 1); entrega.setAtualizadoEm(agora);
                return new Reserva(entrega.getId(), entrega.getReserva(), entrega.getNumero(), entrega.getNomeDestinatario(),
                        entrega.getDataReferencia(), entrega.getReferencia(), entrega.getPdf());
            }
            return null;
        });
    }
    private LocalDate hoje() { return LocalDate.ofInstant(clock.instant(), DashboardClockConfig.ZONA); }
    private boolean elegivel(WhatsappEntrega entrega) {
        if (!clock.instant().isBefore(limite(entrega)) || entrega.getUsuario() == null || !entrega.getUsuario().isAtivo()) return false;
        var assinatura = repos.assinatura(entrega.getUsuario().getId());
        return assinatura != null && assinatura.getStatus() == StatusAssinatura.ATIVA
                && assinatura.getRevisao() == entrega.getAssinaturaRevisao() && assinatura.getNumero().equals(entrega.getNumero())
                && disponibilidade.permitido(entrega.getNumero());
    }
    private Instant limite(WhatsappEntrega entrega) {
        return (entrega.getLote().getOrigem() == Origem.AUTOMATICA ? entrega.getDataReferencia().atTime(18, 0)
                : entrega.getDataReferencia().plusDays(1).atStartOfDay()).atZone(DashboardClockConfig.ZONA).toInstant();
    }
    private boolean aindaElegivel(Reserva reserva) {
        return Boolean.TRUE.equals(tx.execute(s -> {
            repos.bloquearConfiguracao();
            var entrega = repos.buscar(WhatsappEntrega.class, reserva.entregaId());
            return entrega != null && Objects.equals(entrega.getReserva(), reserva.token()) && elegivel(entrega) && disponibilidade.envioConfigurado();
        }));
    }
    private void suprimir(Reserva reserva) {
        tx.executeWithoutResult(s -> {
            repos.bloquearConfiguracao();
            var entrega = repos.buscar(WhatsappEntrega.class, reserva.entregaId());
            if (entrega != null && Objects.equals(entrega.getReserva(), reserva.token())) {
                entrega.setStatus(StatusEntrega.SUPRIMIDA); entrega.setErro("ENVIO_CANCELADO_ANTES_DA_MENSAGEM"); entrega.setReserva(null); entrega.setAtualizadoEm(clock.instant());
            }
        });
    }
    private void concluir(Reserva reserva, String mensagem, MetaWhatsappException falha) {
        tx.executeWithoutResult(s -> {
            repos.bloquearConfiguracao();
            var entrega = repos.buscar(WhatsappEntrega.class, reserva.entregaId());
            if (entrega == null || !Objects.equals(entrega.getReserva(), reserva.token())) return;
            entrega.setReserva(null); entrega.setAtualizadoEm(clock.instant());
            // Webhook pode chegar antes da resposta HTTP: não regredir seu estado confirmado.
            if (entrega.getMensagemMetaId() != null && entrega.getStatus() != StatusEntrega.PROCESSANDO) return;
            if (falha == null) {
                entrega.setMensagemMetaId(mensagem); entrega.setStatus(StatusEntrega.ACEITA); entrega.setErro(null);
            } else {
                entrega.setErro(falha.getMessage());
                if (falha.incerto()) entrega.setStatus(StatusEntrega.INCERTA);
                else if (falha.repetivel() && entrega.getTentativas() <= 3) {
                    int minutos = switch (entrega.getTentativas()) { case 1 -> 1; case 2 -> 5; default -> 15; };
                    Instant proxima = clock.instant().plus(Duration.ofMinutes(minutos));
                    if (proxima.isBefore(limite(entrega))) {
                        entrega.setStatus(StatusEntrega.PENDENTE); entrega.setProximaTentativa(proxima);
                    } else entrega.setStatus(StatusEntrega.EXPIRADA);
                } else entrega.setStatus(StatusEntrega.FALHA);
            }
        });
    }
}
