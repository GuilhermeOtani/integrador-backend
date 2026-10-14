package com.example.integrador.Whatsapp;

import com.example.integrador.Model.Usuario;
import com.example.integrador.Whatsapp.WhatsappTipos.*;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "whatsapp_lote")
@Getter
@Setter
public class WhatsappLote {
    @Id private UUID id;
    @Column(nullable = false) private LocalDate dataReferencia;
    @Column(unique = true) private LocalDate diaAutomatico;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private Origem origem;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private Publico publico;
    @Column(nullable = false) private boolean reenvio;
    @Column(length = 500) private String motivo;
    @Column(nullable = false) private Instant criadoEm;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "solicitante_id")
    @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.SET_NULL)
    private Usuario solicitante;
    @Column(nullable = false) private int ignoradosDuplicidade;
}
