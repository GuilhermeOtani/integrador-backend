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

    private String descricao;

    private LocalDate dataAbertura;

    @Enumerated(EnumType.STRING)
    private NivelUrgencia urgencia;

    @Enumerated(EnumType.STRING)
    private StatusChamado status;

    @ManyToOne
    private Onibus onibus;

    @ManyToOne
    private Pessoa responsavel;

}
