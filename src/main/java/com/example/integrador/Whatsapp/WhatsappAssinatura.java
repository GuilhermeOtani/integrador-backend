package com.example.integrador.Whatsapp;

import com.example.integrador.Model.Usuario;
import com.example.integrador.Whatsapp.WhatsappTipos.StatusAssinatura;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity
@Table(name = "whatsapp_assinatura")
@Getter
@Setter
public class WhatsappAssinatura {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.CASCADE)
    @JoinColumn(name = "usuario_id", nullable = false, unique = true) private Usuario usuario;
    @Column(nullable = false, length = 16) private String numero;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private StatusAssinatura status;
    @Column(nullable = false) private Instant consentimentoEm;
    private Instant confirmadoEm;
    private Instant canceladoEm;
    @Column(length = 64) private String codigoHash;
    private Instant codigoExpiraEm;
    @Column(nullable = false) private Instant janelaAtivacoes;
    @Column(nullable = false) private int ativacoesNaJanela;
    @Column(nullable = false) private long revisao;
    @Column(length = 255) private String ultimoCancelamentoMensagemId;
}
