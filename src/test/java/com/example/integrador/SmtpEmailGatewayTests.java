package com.example.integrador;

import com.example.integrador.Email.*;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import java.net.ConnectException;
import java.util.Properties;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SmtpEmailGatewayTests {
    private final JavaMailSender sender = mock(JavaMailSender.class);
    private final EmailProperties properties = new EmailProperties();
    private final EmailGateway gateway = new SmtpEmailGateway(sender, properties);
    private final EmailDto.Conteudo conteudo = new EmailDto.Conteudo("Programação São José", "Texto", "<p>Texto</p>");

    private MimeMessage mensagem() {
        properties.setFrom("transporte@example.test");
        MimeMessage mensagem = new MimeMessage(Session.getInstance(new Properties()));
        when(sender.createMimeMessage()).thenReturn(mensagem);
        return mensagem;
    }
    @Test void mensagemIndividualUtf8ComTextoEHtmlSemAnexoOuOutrosDestinatarios() throws Exception {
        MimeMessage mensagem = mensagem();
        gateway.enviar(UUID.randomUUID(), "aluno@example.test", conteudo);
        mensagem.saveChanges();
        assertThat(mensagem.getAllRecipients()).hasSize(1);
        assertThat(mensagem.getRecipients(jakarta.mail.Message.RecipientType.CC)).isNull();
        assertThat(mensagem.getRecipients(jakarta.mail.Message.RecipientType.BCC)).isNull();
        assertThat(mensagem.getSubject()).isEqualTo(conteudo.assunto());
        var bytes = new java.io.ByteArrayOutputStream();
        mensagem.writeTo(bytes);
        assertThat(bytes.toString(java.nio.charset.StandardCharsets.UTF_8))
                .contains("multipart/alternative", "text/plain", "text/html").doesNotContain("attachment", "application/pdf");
        verify(sender).send(mensagem);
    }
    @Test void autenticacaoFalhaSemRepetirNemExporSenha() {
        mensagem();
        doThrow(new MailAuthenticationException("senha-secreta")).when(sender).send(any(MimeMessage.class));
        assertThatThrownBy(() -> gateway.enviar(UUID.randomUUID(), "aluno@example.test", conteudo))
                .isInstanceOfSatisfying(EmailEnvioException.class, e -> {
                    assertThat(e.isIncerto()).isFalse();
                    assertThat(e.isRepetivel()).isFalse();
                    assertThat(e.getMessage()).doesNotContain("senha-secreta");
                });
    }
    @Test void conexaoRecusadaAntesDoEnvioPermiteRepetir() {
        mensagem();
        doThrow(new MailSendException("Conexão", new ConnectException())).when(sender).send(any(MimeMessage.class));
        assertThatThrownBy(() -> gateway.enviar(UUID.randomUUID(), "aluno@example.test", conteudo))
                .isInstanceOfSatisfying(EmailEnvioException.class, e -> {
                    assertThat(e.isRepetivel()).isTrue(); assertThat(e.isIncerto()).isFalse();
                });
    }
    @Test void timeoutOuFalhaGenericaFicaIncertoSemRepetir() {
        mensagem();
        doThrow(new MailSendException("Timeout", new java.net.SocketTimeoutException())).when(sender).send(any(MimeMessage.class));
        assertThatThrownBy(() -> gateway.enviar(UUID.randomUUID(), "aluno@example.test", conteudo))
                .isInstanceOfSatisfying(EmailEnvioException.class, e -> {
                    assertThat(e.isRepetivel()).isFalse(); assertThat(e.isIncerto()).isTrue();
                });
    }
}
