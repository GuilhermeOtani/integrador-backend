package com.example.integrador.Model;

import com.example.integrador.Enum.StatusMatricula;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Getter
@Setter
public class Aluno extends Pessoa {

    @Column(name = "matricula")
    private String matricula;
    @Enumerated(EnumType.STRING)
    @Column(name = "status_matricula")
    private StatusMatricula statusMatricula;
    @Column(name = "data_cadastro")
    private LocalDate dataCadastro;
    @Column(name = "mensalidade")
    private BigDecimal mensalidade;

    @ManyToOne
    private Faculdade faculdade;

}
