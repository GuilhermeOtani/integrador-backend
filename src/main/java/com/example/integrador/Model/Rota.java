package com.example.integrador.Model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Entity
@Getter
@Setter
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

    @ManyToMany(cascade = CascadeType.MERGE)
    private List<PontoEmbarque> pontosEmbarque;

}
