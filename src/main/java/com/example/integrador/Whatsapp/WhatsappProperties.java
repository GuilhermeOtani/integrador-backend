package com.example.integrador.Whatsapp;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.HashSet;
import java.util.Set;

/** Nunca serializar esta configuração: contém credenciais exclusivamente do servidor. */
@Getter
@Setter
@ConfigurationProperties("app.whatsapp")
public class WhatsappProperties {
    private boolean enabled;
    private boolean schedulersEnabled = true;
    private boolean production;
    private String graphApiVersion = "";
    private String accessToken = "";
    private String wabaId = "";
    private String phoneNumberId = "";
    private String businessNumber = "";
    private String appSecret = "";
    private String verifyToken = "";
    private String templateName = "";
    private Set<String> testAllowedNumbers = new HashSet<>();
}
