package com.example.integrador;

import com.example.integrador.Whatsapp.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import java.net.SocketTimeoutException;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class MetaWhatsappClientTests {
    private MockRestServiceServer server;
    private MetaWhatsappClient client;
    @BeforeEach void preparar() {
        var builder = RestClient.builder().baseUrl("https://graph.facebook.com/v26.0");
        server = MockRestServiceServer.bindTo(builder).build();
        var p = new WhatsappProperties(); p.setPhoneNumberId("456"); p.setWabaId("123");
        p.setBusinessNumber("+5511999999999"); p.setAccessToken("token-ficticio"); p.setTemplateName("programacao_transporte");
        client = new MetaWhatsappClient(builder.build(), p);
    }
    @AfterEach void verificar() { server.verify(); }

    @Test void numeroETemplateDocumentoAprovados() {
        server.expect(requestTo("https://graph.facebook.com/v26.0/456?fields=id,display_phone_number"))
                .andExpect(header("Authorization", "Bearer token-ficticio"))
                .andRespond(withSuccess("{\"id\":\"456\",\"display_phone_number\":\"+55 11 99999-9999\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://graph.facebook.com/v26.0/123/message_templates?name=programacao_transporte&limit=100"))
                .andRespond(withSuccess("{\"data\":[{\"name\":\"programacao_transporte\",\"language\":\"pt_BR\",\"status\":\"APPROVED\",\"components\":[{\"type\":\"HEADER\",\"format\":\"DOCUMENT\"},{\"type\":\"BODY\",\"text\":\"Olá {{1}}, grade {{2}} de {{3}}.\"}]}]}", MediaType.APPLICATION_JSON));
        client.verificarTemplateENumero();
    }
    @Test void uploadMultipartEEnvioUsamMediaIdECorrelacaoSemURLPublica() {
        server.expect(requestTo("https://graph.facebook.com/v26.0/456/media")).andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.MULTIPART_FORM_DATA))
                .andRespond(withSuccess("{\"id\":\"media-1\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://graph.facebook.com/v26.0/456/messages")).andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.to").value("5511999990001")).andExpect(jsonPath("$.type").value("template"))
                .andExpect(jsonPath("$.template.components[0].parameters[0].document.id").value("media-1"))
                .andExpect(jsonPath("$.biz_opaque_callback_data").value("correlacao"))
                .andRespond(withSuccess("{\"messages\":[{\"id\":\"wamid.1\"}]}", MediaType.APPLICATION_JSON));
        assertThat(client.uploadPdf("%PDF".getBytes(), "programacao.pdf")).isEqualTo("media-1");
        assertThat(client.enviarProgramacao("+5511999990001", "Ana", "14/10/2026", "Faculdade", "media-1", "correlacao")).isEqualTo("wamid.1");
    }
    @Test void timeoutDeMensagemEIncertoENaoRepetivel() {
        server.expect(requestTo("https://graph.facebook.com/v26.0/456/messages")).andRespond(withException(new SocketTimeoutException("timeout")));
        assertThatThrownBy(() -> client.enviarProgramacao("+5511999990001", "Ana", "14/10/2026", "Faculdade", "media", "id"))
                .isInstanceOfSatisfying(MetaWhatsappException.class, e -> { assertThat(e.incerto()).isTrue(); assertThat(e.repetivel()).isFalse(); });
    }
    @Test void erro500DeMensagemEIncertoMas429ERepetivel() {
        server.expect(requestTo("https://graph.facebook.com/v26.0/456/messages")).andRespond(withServerError());
        server.expect(requestTo("https://graph.facebook.com/v26.0/456/messages")).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        assertThatThrownBy(() -> client.enviarProgramacao("+5511999990001", "Ana", "14/10/2026", "Faculdade", "media", "id"))
                .isInstanceOfSatisfying(MetaWhatsappException.class, e -> assertThat(e.incerto()).isTrue());
        assertThatThrownBy(() -> client.enviarProgramacao("+5511999990001", "Ana", "14/10/2026", "Faculdade", "media", "id"))
                .isInstanceOfSatisfying(MetaWhatsappException.class, e -> { assertThat(e.incerto()).isFalse(); assertThat(e.repetivel()).isTrue(); });
    }
    @Test void erroDeUploadNaoEEnvioIncerto() {
        server.expect(requestTo("https://graph.facebook.com/v26.0/456/media")).andRespond(withServerError());
        assertThatThrownBy(() -> client.uploadPdf("%PDF".getBytes(), "programacao.pdf"))
                .isInstanceOfSatisfying(MetaWhatsappException.class, e -> { assertThat(e.incerto()).isFalse(); assertThat(e.repetivel()).isTrue(); });
    }
}
