package com.example.integrador.Model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class PontoEmbarque {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "ordem_parada")
    private Integer ordemParada;
    @Column(name = "descricao")
    private String descricao;

    @ManyToOne
    @JsonIgnore
    @JoinColumn(name = "rota_id")
    private Rota rota;
}
