package com.example.integrador.Whatsapp;

import com.example.integrador.Model.Usuario;
import com.example.integrador.Whatsapp.WhatsappTipos.StatusEntrega;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "whatsapp_entrega", uniqueConstraints =
        @UniqueConstraint(name = "uk_whatsapp_entrega_diaria", columnNames = {"data_referencia", "destinatario_original_id", "revisao"}))
@Getter
@Setter
public class WhatsappEntrega {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lote_id", nullable = false) private WhatsappLote lote;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "usuario_id")
    @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.SET_NULL)
    private Usuario usuario;
    @Column(nullable = false) private Long destinatarioOriginalId;
    @Column(nullable = false) private String nomeDestinatario;
    @Column(nullable = false, length = 16) private String numero;
    @Column(nullable = false) private LocalDate dataReferencia;
    @Column(nullable = false) private int revisao;
    @Column(nullable = false) private long assinaturaRevisao;
    @Column(nullable = false, length = 1000) private String referencia;
    @Column(columnDefinition = "text") private String programacaoJson;
    @Column(columnDefinition = "bytea") private byte[] pdf;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private StatusEntrega status;
    @Column(nullable = false) private int tentativas;
    @Column(nullable = false) private Instant criadoEm;
    private Instant atualizadoEm;
    private Instant proximaTentativa;
    private Instant processamentoEm;
    private UUID reserva;
    @Column(unique = true) private String mensagemMetaId;
    @Column(length = 255) private String erro;
}
