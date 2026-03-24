package com.example.integrador.Model;

import com.example.integrador.Enum.StatusOnibus;
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
    @Column(name = "foto_url")
    private String fotoUrl;
    @Column(name = "capacidade")
    private String Capacidade;
    @ManyToOne
    private Motorista motorista;

    public Motorista getMotorista() {
        return motorista;
    }

    public void setMotorista(Motorista motorista) {
        this.motorista = motorista;
    }

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

    public String getCapacidade() {
        return Capacidade;
    }

    public void setCapacidade(String capacidade) {
        Capacidade = capacidade;
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
