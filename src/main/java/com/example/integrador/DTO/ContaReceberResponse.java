package com.example.integrador.DTO;

import com.example.integrador.Enum.StatusFinanceiro;
import com.example.integrador.Model.ContaReceber;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ContaReceberResponse(
        Long id,
        String descricao,
        BigDecimal valor,
        LocalDate dataVencimento,
        StatusFinanceiro status,
        Long alunoId,
        String nomeAluno
) {
    public static ContaReceberResponse fromEntity(ContaReceber conta) {
        return new ContaReceberResponse(
                conta.getId(),
                conta.getDescricao(),
                conta.getValor(),
                conta.getDataVencimento(),
                conta.getStatus(),
                conta.getAluno().getId(),
                conta.getAluno().getNome());
    }
}
