package com.example.integrador.Model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
public class Motorista extends Pessoa {

    @Column(name = "cnh")
    private String cnh;
    @Column(name = "salario")
    private BigDecimal salario;
    @ManyToOne
    private Onibus onibus;

    public Onibus getOnibus() {
        return onibus;
    }

    public void setOnibus(Onibus onibus) {
        this.onibus = onibus;
    }

    public BigDecimal getSalario() {
        return salario;
    }

    public void setSalario(BigDecimal salario) {
        this.salario = salario;
    }

    public String getCnh() {
        return cnh;
    }

    public void setCnh(String cnh) {
        this.cnh = cnh;
    }
}
