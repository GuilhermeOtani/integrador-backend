package com.example.integrador.Model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Getter
@Setter
public class Motorista extends Pessoa {

    @Column(name = "cnh")
    private String cnh;
    @Column(name = "salario")
    private BigDecimal salario;

}
