package com.example.integrador.DTO;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record AtualizarEmailUsuarioRequest(@NotBlank @Email String email) {
}
