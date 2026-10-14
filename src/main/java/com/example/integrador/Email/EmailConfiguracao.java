package com.example.integrador.Email;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity
@Table(name = "email_configuracao")
@Getter
@Setter
public class EmailConfiguracao {
    @Id
    private Long id = 1L;
    @Column(nullable = false)
    private boolean automatico;
    private Instant ultimaReserva;
}
