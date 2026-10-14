package com.example.integrador.Whatsapp;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity
@Table(name = "whatsapp_configuracao")
@Getter
@Setter
public class WhatsappConfig {
    @Id private Long id = 1L;
    @Column(nullable = false) private boolean automatico;
    private Instant ultimaReserva;
    private Instant webhookVerificadoEm;
    private String webhookFingerprint;
    private Instant templateVerificadoEm;
    private String templateFingerprint;
}
