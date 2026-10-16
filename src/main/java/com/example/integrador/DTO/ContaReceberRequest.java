package com.example.integrador.DTO;

import com.example.integrador.Enum.StatusFinanceiro;
import com.example.integrador.Exception.RegraNegocioException;
import com.example.integrador.Model.Aluno;
import com.example.integrador.Model.ContaReceber;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

public record ContaReceberRequest(Long id, String descricao,
        @NotNull @DecimalMin("0.01") @Digits(integer = 36, fraction = 2) BigDecimal valor,
        @NotNull LocalDate dataVencimento, StatusFinanceiro status,
        @NotNull @Valid AlunoReferencia aluno, String competencia) {
    public record AlunoReferencia(@NotNull @Positive Long id) {}
    public ContaReceber toEntity() {
        ContaReceber c = new ContaReceber(); c.setId(id); c.setDescricao(descricao); c.setValor(valor);
        c.setDataVencimento(dataVencimento); c.setStatus(status);
        if (aluno != null) { Aluno a = new Aluno(); a.setId(aluno.id()); c.setAluno(a); }
        if (competencia != null) {
            if (!competencia.matches("[0-9]{4}-(0[1-9]|1[0-2])") || competencia.startsWith("0000"))
                throw new RegraNegocioException("Informe a competência no formato yyyy-MM.");
            c.setCompetencia(YearMonth.parse(competencia).atDay(1));
        }
        return c;
    }
}
