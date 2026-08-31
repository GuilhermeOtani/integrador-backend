package com.example.integrador.Model;

import com.example.integrador.Enum.DiaSemana;
import com.example.integrador.Enum.TipoPessoa;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
public class GradeDiaria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Enumerated(EnumType.STRING)
    private DiaSemana diaSemana;
    private String descricao;
    @OneToMany(mappedBy = "gradeDiaria", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Viagem> viagens = new ArrayList<>();

}
