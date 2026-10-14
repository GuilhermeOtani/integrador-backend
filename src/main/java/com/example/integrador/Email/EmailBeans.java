package com.example.integrador.Email;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.charset.StandardCharsets;

@Configuration
@EnableConfigurationProperties(EmailProperties.class)
public class EmailBeans {
    @Bean
    public JavaMailSender emailSender(EmailProperties properties) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(properties.getSmtpHost());
        sender.setPort(properties.getSmtpPort());
        sender.setUsername(properties.getSmtpUsername());
        sender.setPassword(properties.getSmtpPassword());
        sender.setDefaultEncoding(StandardCharsets.UTF_8.name());
        var smtp = sender.getJavaMailProperties();
        smtp.setProperty("mail.smtp.auth", Boolean.toString(!properties.getSmtpUsername().isBlank()));
        smtp.setProperty("mail.smtp.starttls.enable", Boolean.toString(properties.isStarttls()));
        smtp.setProperty("mail.smtp.starttls.required", Boolean.toString(properties.isStarttls()));
        smtp.setProperty("mail.smtp.ssl.enable", Boolean.toString(properties.isSsl()));
        smtp.setProperty("mail.smtp.ssl.checkserveridentity", "true");
        smtp.setProperty("mail.smtp.connectiontimeout", "5000");
        smtp.setProperty("mail.smtp.timeout", "20000");
        smtp.setProperty("mail.smtp.writetimeout", "20000");
        return sender;
    }
    @Bean
    public ApplicationRunner inicializarEmail(EmailRepository repository, PlatformTransactionManager manager) {
        return args -> new TransactionTemplate(manager).executeWithoutResult(status -> {
            if (repository.configuracao() == null) repository.persistir(new EmailConfiguracao());
        });
    }
}
