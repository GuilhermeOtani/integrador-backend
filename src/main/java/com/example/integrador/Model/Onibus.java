package com.example.integrador.Model;

import com.example.integrador.Enum.StatusOnibus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "onibus")
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
    @Column(name = "foto_url", columnDefinition = "TEXT")
    private String fotoUrl;
    @Column(name = "capacidade")
    private int capacidade;
}
