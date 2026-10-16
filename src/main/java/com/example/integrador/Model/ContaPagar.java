package com.example.integrador.Model;

import com.example.integrador.Enum.StatusFinanceiro;
import com.example.integrador.Enum.ClassificacaoConta;
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

    /** Primeiro dia do mês; o contrato HTTP usa yyyy-MM. */
    private LocalDate competencia;

    @Enumerated(EnumType.STRING)
    private ClassificacaoConta classificacao;

    /** Dia original, preservado mesmo quando fevereiro encurta o vencimento. */
    private Integer diaVencimentoSalario;

}
