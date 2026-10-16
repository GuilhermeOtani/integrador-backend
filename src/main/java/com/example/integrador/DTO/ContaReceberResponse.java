package com.example.integrador.DTO;

import com.example.integrador.Enum.StatusFinanceiro;
import com.example.integrador.Model.ContaReceber;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;

public record ContaReceberResponse(Long id, String descricao, BigDecimal valor, LocalDate dataVencimento,
        StatusFinanceiro status, Long alunoId, String nomeAluno, String competencia,
        BigDecimal valorRecebido, BigDecimal saldoRestante, boolean recebimentoParcial, List<String> avisos) {
    public static ContaReceberResponse fromEntity(ContaReceber c, PosicaoContaReceber p) {
        return new ContaReceberResponse(c.getId(), c.getDescricao(), c.getValor(), c.getDataVencimento(), c.getStatus(),
                c.getAluno().getId(), c.getAluno().getNome(), c.getCompetencia() == null ? null : YearMonth.from(c.getCompetencia()).toString(),
                p.valorRecebido(), p.saldoRestante(), p.recebimentoParcial(), p.avisos());
    }
}
