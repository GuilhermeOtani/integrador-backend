package com.example.integrador.Email;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@ConfigurationProperties("app.email")
public class EmailProperties {
    private boolean enabled;
    private boolean schedulersEnabled = true;
    private boolean production;
    private Set<String> allowedRecipients = new HashSet<>();
    private String from = "";
    private String smtpHost = "";
    private int smtpPort = 587;
    private String smtpUsername = "";
    private String smtpPassword = "";
    private boolean starttls = true;
    private boolean ssl;
}
