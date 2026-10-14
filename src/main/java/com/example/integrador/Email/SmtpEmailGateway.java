package com.example.integrador.Email;

import jakarta.mail.MessagingException;
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
            throw new EmailEnvioException("AUTENTICACAO_SMTP", false, false);
        } catch (MailSendException exception) {
            boolean antesDoEnvio = exception.getFailedMessages().isEmpty()
                    && (temCausa(exception, ConnectException.class) || temCausa(exception, UnknownHostException.class));
            throw new EmailEnvioException(antesDoEnvio ? "CONEXAO_SMTP" : "RESULTADO_SMTP_INCERTO",
                    !antesDoEnvio, antesDoEnvio);
        } catch (MailException exception) {
            throw new EmailEnvioException("RESULTADO_SMTP_INCERTO", true, false);
        } catch (MessagingException exception) {
            throw new EmailEnvioException("MENSAGEM_INVALIDA", false, false);
        }
    }
    private boolean temCausa(Throwable exception, Class<? extends Throwable> tipo) {
        for (int i = 0; exception != null && i < 20; i++, exception = exception.getCause())
            if (tipo.isInstance(exception)) return true;
        return false;
    }
}
