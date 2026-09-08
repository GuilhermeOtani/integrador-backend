package com.example.integrador.DTO;

import com.example.integrador.Enum.TipoPessoa;
import com.example.integrador.Model.Usuario;

public record UsuarioResponse(
        Long id,
        String email,
        TipoPessoa tipoPessoa,
        Long pessoaId,
        String nome,
        boolean ativo
) {
    public static UsuarioResponse fromEntity(Usuario usuario) {
        return new UsuarioResponse(
                usuario.getId(),
                usuario.getEmail(),
                usuario.getTipoPessoa(),
                usuario.getPessoa().getId(),
                usuario.getPessoa().getNome(),
                usuario.isAtivo()
        );
    }
}
