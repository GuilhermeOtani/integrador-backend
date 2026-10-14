package com.example.integrador.Email;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "email_entrega", uniqueConstraints =
        @UniqueConstraint(name = "uq_email_dia_destinatario_revisao", columnNames = {"data_referencia", "email", "revisao"}))
@Getter
@Setter
public class EmailEntrega {
    @Id
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lote_id", nullable = false)
    private EmailLote lote;
    @Column(nullable = false)
    private Long usuarioId;
    @Column(nullable = false, length = 255)
    private String email;
    @Column(nullable = false)
    private LocalDate dataReferencia;
    @Column(nullable = false)
    private int revisao;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EmailDto.Estado estado = EmailDto.Estado.PENDENTE;
    @Column(nullable = false)
    private int tentativas;
    @Column(nullable = false)
    private Instant proximaTentativa;
    private Instant reservadoEm;
    private UUID reservaId;
    private Instant aceitoEm;
    @Column(length = 100)
    private String erro;
}
