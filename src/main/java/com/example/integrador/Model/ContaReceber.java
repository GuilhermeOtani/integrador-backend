package com.example.integrador.Model;

import com.example.integrador.Enum.StatusFinanceiro;
import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
public class ContaReceber {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Double valor;

    private String descricao;

    private LocalDate dataVencimento;

    @Enumerated(EnumType.STRING)
    private StatusFinanceiro status;

    @ManyToOne
    private Aluno aluno;

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Double getValor() {
        return valor;
    }

    public void setValor(Double valor) {
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

    public Aluno getAluno() {
        return aluno;
    }

    public void setAluno(Aluno aluno) {
        this.aluno = aluno;
    }
}
