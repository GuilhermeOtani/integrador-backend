package com.example.integrador.Model;

import com.example.integrador.Enum.StatusFinanceiro;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Getter
@Setter
public class ContaPagar {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String descricao;

    private BigDecimal valor;

    private LocalDate dataVencimento;

    @Enumerated(EnumType.STRING)
    private StatusFinanceiro status;

    @ManyToOne
    @JoinColumn(name = "pessoa_id")
    private Pessoa motorista;

}