package com.example.integrador.Model;

import com.example.integrador.Enum.StatusMatricula;
import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
public class Aluno extends Pessoa {

    @Column(name = "matricula")
    private String matricula;
    @Enumerated(EnumType.STRING)
    @Column(name = "status_matricula")
    private StatusMatricula statusMatricula;
    @Column(name = "data_cadastro")
    private LocalDate dataCadastro;

    @ManyToOne
    private Viagem viagem;

    @ManyToOne
    private Faculdade faculdade;

    @ManyToOne
    private Rota rota;

    public Viagem getViagem() {
        return viagem;
    }

    public void setViagem(Viagem viagem) {
        this.viagem = viagem;
    }

    public Rota getRota() {
        return rota;
    }

    public void setRota(Rota rota) {
        this.rota = rota;
    }

    public Faculdade getFaculdade() {
        return faculdade;
    }

    public void setFaculdade(Faculdade faculdade) {
        this.faculdade = faculdade;
    }

    public String getMatricula() {
        return matricula;
    }

    public void setMatricula(String matricula) {
        this.matricula = matricula;
    }

    public StatusMatricula getStatusMatricula() {
        return statusMatricula;
    }

    public void setStatusMatricula(StatusMatricula statusMatricula) {
        this.statusMatricula = statusMatricula;
    }

    public LocalDate getDataCadastro() {
        return dataCadastro;
    }

    public void setDataCadastro(LocalDate dataCadastro) {
        this.dataCadastro = dataCadastro;
    }
}
