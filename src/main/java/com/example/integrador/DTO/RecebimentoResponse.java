package com.example.integrador.DTO;

import com.example.integrador.Enum.FormaPagamento;
import com.example.integrador.Model.Recebimento;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RecebimentoResponse(
        Long id,
        Long contaId,
        BigDecimal valor,
        LocalDate dataRecebimento,
        FormaPagamento formaPagamento,
        String nomeAluno,
        String descricaoConta
) {
    public static RecebimentoResponse fromEntity(Recebimento recebimento) {
        return new RecebimentoResponse(
                recebimento.getId(),
                recebimento.getContaReceber().getId(),
                recebimento.getValor(),
                recebimento.getDataRecebimento(),
                recebimento.getFormaPagamento(),
                recebimento.getContaReceber().getAluno().getNome(),
                recebimento.getContaReceber().getDescricao());
    }
}
