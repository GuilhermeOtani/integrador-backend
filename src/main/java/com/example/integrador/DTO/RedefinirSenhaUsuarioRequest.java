package com.example.integrador.DTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RedefinirSenhaUsuarioRequest(
        @NotBlank @Size(min = 8, max = 72) String senha
) {
}
