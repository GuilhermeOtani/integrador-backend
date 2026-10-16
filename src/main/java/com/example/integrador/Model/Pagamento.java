package com.example.integrador.Model;

import com.example.integrador.Enum.FormaPagamento;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Getter
@Setter
public class Pagamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private BigDecimal valor;

    private LocalDate dataPagamento;

    @Enumerated(EnumType.STRING)
    private FormaPagamento formaPagamento;

    @ManyToOne
    @JoinColumn(name = "conta_pagar_id")
    @JsonIgnoreProperties({"pagamentos", "motorista"})
    private ContaPagar contaPagar;

    @JsonIgnore
    @Column(length = 36, unique = true)
    private String chaveIdempotencia;

    @JsonIgnore
    private String requisicaoIdempotencia;
    }

