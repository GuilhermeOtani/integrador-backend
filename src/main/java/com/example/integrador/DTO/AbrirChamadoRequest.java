package com.example.integrador.DTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record AbrirChamadoRequest(
        @NotNull @Positive Long onibusId,
        @NotBlank @Size(max = 2000) String descricao
) {}
