package com.example.integrador.DTO;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CriarUsuarioRequest(
        @NotNull Long pessoaId,
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, max = 72) String senha
) {
}
