package com.example.integrador.DTO;

import jakarta.validation.constraints.NotNull;

public record AtualizarStatusUsuarioRequest(@NotNull Boolean ativo) {
}
