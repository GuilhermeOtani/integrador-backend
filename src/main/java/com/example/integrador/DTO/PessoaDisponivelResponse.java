package com.example.integrador.DTO;

import com.example.integrador.Enum.TipoPessoa;

public record PessoaDisponivelResponse(Long id, String nome, TipoPessoa tipoPessoa) {
}
