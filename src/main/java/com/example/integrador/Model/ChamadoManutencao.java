package com.example.integrador.Model;

import com.example.integrador.Enum.NivelUrgencia;
import com.example.integrador.Enum.StatusChamado;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Getter
@Setter
public class ChamadoManutencao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 2000)
    private String descricao;

    @Column(nullable = false)
    private LocalDate dataAbertura;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NivelUrgencia urgencia;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusChamado status;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "onibus_id", nullable = false)
    private Onibus onibus;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "responsavel_id", nullable = false)
    private Pessoa solicitante;

    @Column(length = 2000)
    private String observacaoAdmin;

}
