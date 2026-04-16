package com.example.integrador.Model;

import com.example.integrador.Enum.StatusOnibus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

@Entity
public class Onibus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "numeroIdentificacao")
    private String numeroIdentificacao;
    @Column(name = "placa")
    private String placa;
    @Column(name = "modelo")
    private String modelo;
    @Enumerated(EnumType.STRING)
    @Column(name = "status_onibus")
    private StatusOnibus statusOnibus;
    @Lob
    @Column(name = "foto_url", columnDefinition = "LONGTEXT")
    private String fotoUrl;
    @Column(name = "capacidade")
    private Double capacidade;


    public StatusOnibus getStatusOnibus() {
        return statusOnibus;
    }

    public void setStatusOnibus(StatusOnibus statusOnibus) {
        this.statusOnibus = statusOnibus;
    }

    public String getFotoUrl() {
        return fotoUrl;
    }

    public void setFotoUrl(String fotoUrl) {
        this.fotoUrl = fotoUrl;
    }

    public Double getCapacidade() {
        return capacidade;
    }

    public void setCapacidade(Double capacidade) {
        this.capacidade = capacidade;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNumeroIdentificacao() {
        return numeroIdentificacao;
    }

    public void setNumeroIdentificacao(String numeroIdentificacao) {
        this.numeroIdentificacao = numeroIdentificacao;
    }

    public String getPlaca() {
        return placa;
    }

    public void setPlaca(String placa) {
        this.placa = placa;
    }

    public String getModelo() {
        return modelo;
    }

    public void setModelo(String modelo) {
        this.modelo = modelo;
    }

}
