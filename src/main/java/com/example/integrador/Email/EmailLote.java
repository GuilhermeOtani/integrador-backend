package com.example.integrador.Email;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "email_lote")
@Getter
@Setter
public class EmailLote {
    @Id
    private UUID id;
    @Column(nullable = false)
    private LocalDate dataReferencia;
    @Column(unique = true)
    private LocalDate diaAutomatico;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EmailDto.Origem origem;
    @Column(nullable = false)
    private boolean reenvio;
    @Column(length = 500)
    private String motivo;
    @Column(nullable = false)
    private Instant criadoEm;
    // Identificação histórica, sem FK que impeça excluir somente o acesso.
    private Long solicitanteId;
    @Column(nullable = false)
    private int ignorados;
    @Column(nullable = false, length = 255)
    private String assunto;
    @Column(nullable = false, columnDefinition = "text")
    private String texto;
    @Column(nullable = false, columnDefinition = "text")
    private String html;
}
