package com.example.integrador.Model;

import com.example.integrador.Enum.StatusFinanceiro;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
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


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public void setValor(BigDecimal valor) {
        this.valor = valor;
    }

    public LocalDate getDataVencimento() {
        return dataVencimento;
    }

    public void setDataVencimento(LocalDate dataVencimento) {
        this.dataVencimento = dataVencimento;
    }

    public StatusFinanceiro getStatus() {
        return status;
    }

    public void setStatus(StatusFinanceiro status) {
        this.status = status;
    }

    public Pessoa getMotorista() {
        return motorista;
    }

    public void setMotorista(Pessoa motorista) {
        this.motorista = motorista;
    }
}