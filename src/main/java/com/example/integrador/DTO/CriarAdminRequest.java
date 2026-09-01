package com.example.integrador.DTO;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CriarAdminRequest(
        @NotBlank @Size(max = 255) String nome,
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, max = 72) String senha
) {
}
