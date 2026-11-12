package com.example.integrador.Email;

import jakarta.mail.MessagingException;
import org.eclipse.angus.mail.util.MailConnectException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Component
public class SmtpEmailGateway implements EmailGateway {
    private static final Logger LOG = LoggerFactory.getLogger(SmtpEmailGateway.class);
    private final JavaMailSender sender;
    private final EmailProperties properties;
    public SmtpEmailGateway(JavaMailSender sender, EmailProperties properties) {
        this.sender = sender;
        this.properties = properties;
    }
    @Override
    public void enviar(UUID entregaId, String destinatario, EmailDto.Conteudo conteudo) {
        try {
            var mensagem = sender.createMimeMessage();
            var helper = new MimeMessageHelper(mensagem, true, StandardCharsets.UTF_8.name());
            helper.setFrom(properties.getFrom());
            helper.setTo(destinatario); // Uma única pessoa, sem CC/BCC ou lista visível.
            helper.setSubject(conteudo.assunto());
            helper.setText(conteudo.texto(), conteudo.html());
            mensagem.setHeader("X-Integrador-Entrega", entregaId.toString());
            sender.send(mensagem);
        } catch (MailAuthenticationException exception) {
            throw falha(entregaId, "AUTENTICACAO_SMTP", false, false, exception);
        } catch (MailSendException exception) {
            // Spring também preenche failedMessages quando nem conseguiu abrir a conexão.
            boolean antesDoEnvio = falhaDeConexao(exception)
                    || exception.getFailedMessages().values().stream().anyMatch(this::falhaDeConexao);
            throw falha(entregaId, antesDoEnvio ? "CONEXAO_SMTP" : "RESULTADO_SMTP_INCERTO",
                    !antesDoEnvio, antesDoEnvio, exception);
        } catch (MailException exception) {
            throw falha(entregaId, "RESULTADO_SMTP_INCERTO", true, false, exception);
        } catch (MessagingException exception) {
            throw falha(entregaId, "MENSAGEM_INVALIDA", false, false, exception);
        }
    }
    private boolean falhaDeConexao(Throwable exception) {
        return temCausa(exception, MailConnectException.class)
                || temCausa(exception, ConnectException.class)
                || temCausa(exception, UnknownHostException.class);
    }
    private EmailEnvioException falha(UUID entregaId, String codigo, boolean incerto, boolean repetivel,
                                      Throwable causa) {
        if (causa instanceof MailSendException envio && causa.getCause() == null
                && !envio.getFailedMessages().isEmpty()) {
            causa = envio.getFailedMessages().values().iterator().next();
        }
        for (int i = 0; causa.getCause() != null && i < 20; i++) causa = causa.getCause();
        // Não registrar mensagens/stack traces SMTP: podem conter endereço e credencial.
        LOG.warn("Falha no envio de e-mail {}: codigo={}, causa={}", entregaId, codigo, causa.getClass().getSimpleName());
        return new EmailEnvioException(codigo, incerto, repetivel);
    }
    private boolean temCausa(Throwable exception, Class<? extends Throwable> tipo) {
        for (int i = 0; exception != null && i < 20; i++, exception = exception.getCause())
            if (tipo.isInstance(exception)) return true;
        return false;
    }
}
