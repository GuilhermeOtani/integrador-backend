package com.example.integrador.Whatsapp;

import com.example.integrador.Whatsapp.WhatsappTipos.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;

@Service
public class WhatsappWebhookService {
    private final WhatsappProperties properties;
    private final WhatsappDisponibilidadeService disponibilidade;
    private final WhatsappRepos repos;
    private final WhatsappAssinaturaService assinaturas;
    private final JsonMapper json;
    private final TransactionTemplate tx;
    private final Clock clock;
    public WhatsappWebhookService(WhatsappProperties properties, WhatsappDisponibilidadeService disponibilidade, WhatsappRepos repos,
                                  WhatsappAssinaturaService assinaturas, JsonMapper json, TransactionTemplate tx, Clock clock) {
        this.properties = properties; this.disponibilidade = disponibilidade; this.repos = repos;
        this.assinaturas = assinaturas; this.json = json; this.tx = tx; this.clock = clock;
    }
    public String verificar(String mode, String token, String challenge) {
        if (!"subscribe".equals(mode) || challenge == null || challenge.length() > 1000 || properties.getVerifyToken().isBlank()
                || token == null || !MessageDigest.isEqual(properties.getVerifyToken().getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Verificação de webhook inválida");
        }
        tx.executeWithoutResult(s -> {
            var cfg = repos.bloquearConfiguracao(); cfg.setWebhookVerificadoEm(clock.instant()); cfg.setWebhookFingerprint(disponibilidade.webhookFingerprint());
        });
        return challenge;
    }
    public void receber(byte[] corpo, String assinatura) {
        if (corpo.length > 2_000_000) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Webhook excede o limite");
        if (!assinaturaValida(corpo, assinatura)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Assinatura do webhook inválida");
        final JsonNode raiz;
        try { raiz = json.readTree(corpo); }
        catch (RuntimeException exception) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "JSON inválido"); }
        if (raiz == null || !"whatsapp_business_account".equals(raiz.path("object").asText())) return;
        tx.executeWithoutResult(s -> {
            repos.bloquearConfiguracao();
            for (JsonNode entrada : raiz.path("entry")) {
                if (!properties.getWabaId().equals(entrada.path("id").asText())) continue;
                for (JsonNode alteracao : entrada.path("changes")) {
                    JsonNode valor = alteracao.path("value");
                    if (!properties.getPhoneNumberId().equals(valor.path("metadata").path("phone_number_id").asText())) continue;
                    for (JsonNode mensagem : valor.path("messages")) comando(mensagem);
                    for (JsonNode estado : valor.path("statuses")) atualizarEntrega(estado);
                }
            }
        });
    }
    private boolean assinaturaValida(byte[] corpo, String assinatura) {
        if (properties.getAppSecret().isBlank() || assinatura == null || !assinatura.matches("sha256=[a-fA-F0-9]{64}")) return false;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.getAppSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return MessageDigest.isEqual(mac.doFinal(corpo), HexFormat.of().parseHex(assinatura.substring(7)));
        } catch (GeneralSecurityException | IllegalArgumentException exception) { return false; }
    }
    private void comando(JsonNode mensagem) {
        if (!"text".equals(mensagem.path("type").asText())) return;
        String origem;
        try { origem = WhatsappSeguranca.numero("+" + mensagem.path("from").asText()); }
        catch (RuntimeException exception) { return; }
        String texto = mensagem.path("text").path("body").asText().trim();
        if (Set.of("SAIR", "PARAR", "CANCELAR").contains(texto.toUpperCase(Locale.ROOT))) {
            Instant data = timestamp(mensagem.path("timestamp").asText());
            String mensagemId = mensagem.path("id").asText();
            if (data == null || mensagemId.isBlank() || mensagemId.length() > 255) return;
            for (var assinatura : repos.porNumero(origem)) {
                // Eventos antigos não podem cancelar uma autorização renovada posteriormente.
                if (!mensagemId.equals(assinatura.getUltimoCancelamentoMensagemId())
                        && !data.isBefore(assinatura.getConsentimentoEm().minusSeconds(2))) {
                    assinaturas.cancelar(assinatura);
                    assinatura.setUltimoCancelamentoMensagemId(mensagemId);
                }
            }
        } else if (texto.matches("(?i)ATIVAR [a-f0-9]{48}")) {
            var assinatura = repos.porCodigo(WhatsappSeguranca.hash(texto.substring(7).toLowerCase(Locale.ROOT)));
            if (assinatura == null || assinatura.getStatus() != StatusAssinatura.PENDENTE || !assinatura.getUsuario().isAtivo()
                    || !assinatura.getNumero().equals(origem) || !disponibilidade.permitido(origem)
                    || !clock.instant().isBefore(assinatura.getCodigoExpiraEm())) return;
            boolean ocupado = repos.porNumero(origem).stream().anyMatch(a -> a.getStatus() == StatusAssinatura.ATIVA && !a.getId().equals(assinatura.getId()));
            if (ocupado) return;
            assinatura.setStatus(StatusAssinatura.ATIVA); assinatura.setConfirmadoEm(clock.instant());
            assinatura.setCodigoHash(null); assinatura.setCodigoExpiraEm(null);
        }
    }
    private Instant timestamp(String valor) {
        try { return Instant.ofEpochSecond(Long.parseLong(valor)); } catch (RuntimeException exception) { return null; }
    }
    private void atualizarEntrega(JsonNode evento) {
        String mensagem = evento.path("id").asText();
        if (mensagem.isBlank() || mensagem.length() > 255) return;
        var entrega = repos.porMensagem(mensagem);
        if (entrega == null) {
            try { entrega = repos.buscar(WhatsappEntrega.class, UUID.fromString(evento.path("biz_opaque_callback_data").asText())); }
            catch (IllegalArgumentException exception) { return; }
            if (entrega == null || entrega.getProcessamentoEm() == null || entrega.getMensagemMetaId() != null) return;
            if (!entrega.getNumero().substring(1).equals(evento.path("recipient_id").asText())) return;
            entrega.setMensagemMetaId(mensagem);
        }
        StatusEntrega novo = switch (evento.path("status").asText()) {
            case "sent" -> StatusEntrega.ENVIADA; case "delivered" -> StatusEntrega.ENTREGUE;
            case "read" -> StatusEntrega.LIDA; case "failed" -> StatusEntrega.FALHA; default -> null;
        };
        if (novo == null || novo == StatusEntrega.FALHA && ordem(entrega.getStatus()) >= ordem(StatusEntrega.ENTREGUE)) return;
        if (novo != StatusEntrega.FALHA && ordem(novo) <= ordem(entrega.getStatus())) return;
        entrega.setStatus(novo); entrega.setAtualizadoEm(clock.instant());
        entrega.setErro(novo == StatusEntrega.FALHA ? "ENTREGA_META_FALHOU" : null);
    }
    private int ordem(StatusEntrega status) {
        return switch (status) { case ACEITA -> 1; case ENVIADA -> 2; case ENTREGUE -> 3; case LIDA -> 4; default -> 0; };
    }
}
