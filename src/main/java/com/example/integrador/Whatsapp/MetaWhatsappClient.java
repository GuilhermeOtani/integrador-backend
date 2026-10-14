package com.example.integrador.Whatsapp;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.*;
import tools.jackson.databind.JsonNode;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.net.http.HttpConnectTimeoutException;
import java.util.*;

public class MetaWhatsappClient implements MetaWhatsappGateway {
    private final RestClient http;
    private final WhatsappProperties properties;
    public MetaWhatsappClient(RestClient http, WhatsappProperties properties) { this.http = http; this.properties = properties; }

    @Override
    public void verificarTemplateENumero() {
        JsonNode telefone = executar(false, () -> http.get().uri("/" + properties.getPhoneNumberId() + "?fields=id,display_phone_number")
                .headers(this::autorizacao).retrieve().body(JsonNode.class));
        if (telefone == null || !properties.getPhoneNumberId().equals(telefone.path("id").asText())
                || !telefone.path("display_phone_number").asText().replaceAll("[^0-9]", "")
                    .equals(properties.getBusinessNumber().replaceAll("[^0-9]", ""))) {
            throw new MetaWhatsappException("NUMERO_META_DIVERGENTE", false, false);
        }
        JsonNode templates = executar(false, () -> http.get().uri(builder -> builder.path("/" + properties.getWabaId() + "/message_templates")
                .queryParam("name", properties.getTemplateName()).queryParam("limit", 100).build())
                .headers(this::autorizacao).retrieve().body(JsonNode.class));
        if (templates != null) for (JsonNode template : templates.path("data")) {
            if (properties.getTemplateName().equals(template.path("name").asText())
                    && "pt_BR".equals(template.path("language").asText())
                    && "APPROVED".equals(template.path("status").asText())) {
                boolean documento = false, corpo = false;
                for (JsonNode componente : template.path("components")) {
                    if ("HEADER".equals(componente.path("type").asText()) && "DOCUMENT".equals(componente.path("format").asText())) documento = true;
                    if ("BODY".equals(componente.path("type").asText())) {
                        String texto = componente.path("text").asText();
                        corpo = texto.contains("{{1}}") && texto.contains("{{2}}") && texto.contains("{{3}}") && !texto.contains("{{4}}");
                    }
                }
                if (documento && corpo) return;
            }
        }
        throw new MetaWhatsappException("TEMPLATE_DOCUMENTO_NAO_APROVADO", false, false);
    }

    @Override
    public String uploadPdf(byte[] pdf, String nomeArquivo) {
        var form = new LinkedMultiValueMap<String, Object>();
        form.add("messaging_product", "whatsapp"); form.add("type", "application/pdf");
        var recurso = new ByteArrayResource(pdf) { @Override public String getFilename() { return nomeArquivo; } };
        form.add("file", new HttpEntity<>(recurso, headersPdf()));
        JsonNode resposta = executar(false, () -> http.post().uri("/" + properties.getPhoneNumberId() + "/media")
                .headers(this::autorizacao).contentType(MediaType.MULTIPART_FORM_DATA).body(form).retrieve().body(JsonNode.class));
        if (resposta == null || resposta.path("id").asText().isBlank()) throw new MetaWhatsappException("UPLOAD_SEM_IDENTIFICADOR", false, true);
        return resposta.path("id").asText();
    }
    private HttpHeaders headersPdf() { var headers = new HttpHeaders(); headers.setContentType(MediaType.APPLICATION_PDF); return headers; }

    @Override
    public String enviarProgramacao(String numero, String nome, String data, String referencia, String mediaId, String correlacao) {
        var template = Map.of("name", properties.getTemplateName(), "language", Map.of("code", "pt_BR"),
                "components", List.of(
                    Map.of("type", "header", "parameters", List.of(Map.of("type", "document", "document", Map.of("id", mediaId, "filename", "programacao.pdf")))),
                    Map.of("type", "body", "parameters", List.of(parametro(nome), parametro(data), parametro(referencia)))));
        var corpo = Map.of("messaging_product", "whatsapp", "recipient_type", "individual", "to", numero.substring(1),
                "type", "template", "template", template, "biz_opaque_callback_data", correlacao);
        JsonNode resposta = executar(true, () -> http.post().uri("/" + properties.getPhoneNumberId() + "/messages")
                .headers(this::autorizacao).contentType(MediaType.APPLICATION_JSON).body(corpo).retrieve().body(JsonNode.class));
        String id = resposta == null ? "" : resposta.path("messages").path(0).path("id").asText();
        if (id.isBlank()) throw new MetaWhatsappException("ENVIO_SEM_IDENTIFICADOR", true, false);
        return id;
    }
    private Map<String, String> parametro(String texto) {
        String limpo = ProgramacaoWhatsappService.valor(texto, "Não informado").replaceAll("\\s+", " ").trim();
        return Map.of("type", "text", "text", limpo);
    }
    private void autorizacao(HttpHeaders headers) { headers.setBearerAuth(properties.getAccessToken()); }
    private JsonNode executar(boolean envio, Chamada chamada) {
        try { return chamada.executar(); }
        catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            throw new MetaWhatsappException("META_HTTP_" + status, envio && status >= 500, status == 429 || !envio && status >= 500);
        } catch (ResourceAccessException exception) {
            boolean conexaoNaoAberta = false;
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof ConnectException || cause instanceof UnknownHostException || cause instanceof HttpConnectTimeoutException) conexaoNaoAberta = true;
            }
            throw new MetaWhatsappException("META_CONEXAO", envio && !conexaoNaoAberta, !envio || conexaoNaoAberta);
        } catch (RestClientException exception) {
            throw new MetaWhatsappException("META_RESPOSTA_INVALIDA", envio, !envio);
        }
    }
    private interface Chamada { JsonNode executar(); }
}
