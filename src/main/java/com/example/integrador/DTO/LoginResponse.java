package com.example.integrador.DTO;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        UsuarioResponse usuario
) {
}
