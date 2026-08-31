package com.example.integrador.DTO;

import com.example.integrador.Enum.DiaSemana;
import com.example.integrador.Model.GradeDiaria;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Setter
public class GradeDiariaDTO {

    private Long id;
    private LocalDate data;
    private DiaSemana diaSemana;
    private String descricao;

    private List<ViagemDTO> viagens = new ArrayList<>();

    public GradeDiariaDTO() {
    }

    public GradeDiariaDTO(GradeDiaria gradeDiaria) {
        this.id = gradeDiaria.getId();
        this.diaSemana = gradeDiaria.getDiaSemana();
        this.descricao = gradeDiaria.getDescricao();

        if (gradeDiaria.getViagens() != null) {
            this.viagens = gradeDiaria.getViagens().stream()
                    .map(ViagemDTO::new)
                    .collect(Collectors.toList());
        }
    }
}