package com.example.integrador.Model;


import com.example.integrador.Enum.FormaPagamento;
import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Getter
@Setter
public class Recebimento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private BigDecimal valor;

    private LocalDate dataRecebimento;

    @Enumerated(EnumType.STRING)
    private FormaPagamento formaPagamento;

    @ManyToOne(optional = false)
    @JoinColumn(name = "conta_receber_id", nullable = false)
    private ContaReceber contaReceber;

    @JsonIgnore
    @Column(length = 36, unique = true)
    private String chaveIdempotencia;

    @JsonIgnore
    private String requisicaoIdempotencia;
}
