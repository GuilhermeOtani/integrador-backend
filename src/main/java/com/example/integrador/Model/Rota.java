package com.example.integrador.Model;

import jakarta.persistence.*;

import java.util.List;

@Entity
public class Rota {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "nome")
    private String nome;
    @Column(name = "descricao")
    private String descricao;

    @ManyToOne
    private Faculdade faculdade;

    @ManyToMany
    private List<PontoEmbarque> pontosEmbarque;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public Faculdade getFaculdade() {
        return faculdade;
    }

    public void setFaculdade(Faculdade faculdade) {
        this.faculdade = faculdade;
    }

    public List<PontoEmbarque> getPontosEmbarque() {
        return pontosEmbarque;
    }

    public void setPontosEmbarque(List<PontoEmbarque> pontosEmbarque) {
        this.pontosEmbarque = pontosEmbarque;
    }
}
