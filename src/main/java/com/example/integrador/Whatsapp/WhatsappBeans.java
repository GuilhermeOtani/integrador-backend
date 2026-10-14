package com.example.integrador.Whatsapp;

import jakarta.persistence.EntityManager;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;
import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
@EnableConfigurationProperties(WhatsappProperties.class)
public class WhatsappBeans {
    @Bean
    public MetaWhatsappGateway metaWhatsappGateway(WhatsappProperties properties) {
        var cliente = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
        var factory = new JdkClientHttpRequestFactory(cliente); factory.setReadTimeout(Duration.ofSeconds(20));
        String versao = properties.getGraphApiVersion().matches("v[0-9]+\\.[0-9]+") ? properties.getGraphApiVersion() : "nao-configurado";
        return new MetaWhatsappClient(RestClient.builder().baseUrl("https://graph.facebook.com/" + versao).requestFactory(factory).build(), properties);
    }
    @Bean
    public ApplicationRunner iniciarConfiguracaoWhatsapp(EntityManager em, TransactionTemplate transacao) {
        return args -> transacao.executeWithoutResult(status -> {
            if (em.find(WhatsappConfig.class, 1L) == null) em.persist(new WhatsappConfig());
        });
    }
}
