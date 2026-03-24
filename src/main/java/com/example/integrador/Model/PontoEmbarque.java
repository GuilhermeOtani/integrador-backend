package com.example.integrador.Model;

import jakarta.persistence.*;

@Entity
public class PontoEmbarque {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "ordem_parada")
    private Integer ordemParada;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getOrdemParada() {
        return ordemParada;
    }

    public void setOrdemParada(Integer ordemParada) {
        this.ordemParada = ordemParada;
    }
}
