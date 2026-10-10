package com.example.integrador.DTO;

import com.example.integrador.Enum.NivelUrgencia;
import com.example.integrador.Enum.StatusChamado;
import com.example.integrador.Enum.StatusOnibus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AtenderChamadoRequest(
        @NotNull NivelUrgencia urgencia,
        @NotNull StatusChamado status,
        @Size(max = 2000) String observacaoAdmin,
        StatusOnibus statusOnibus
) {}
