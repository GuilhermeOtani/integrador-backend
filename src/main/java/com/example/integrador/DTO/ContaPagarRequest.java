package com.example.integrador.DTO;

import com.example.integrador.Enum.ClassificacaoConta;
import com.example.integrador.Enum.StatusFinanceiro;
import com.example.integrador.Model.ContaPagar;
import com.example.integrador.Model.Pessoa;
import com.example.integrador.Exception.RegraNegocioException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

public record ContaPagarRequest(Long id, String descricao, BigDecimal valor, LocalDate dataVencimento,
        StatusFinanceiro status, Pessoa motorista, String competencia, ClassificacaoConta classificacao) {
    public ContaPagar toEntity() {
        ContaPagar conta = new ContaPagar();
        conta.setId(id); conta.setDescricao(descricao); conta.setValor(valor);
        conta.setDataVencimento(dataVencimento); conta.setStatus(status); conta.setMotorista(motorista);
        conta.setClassificacao(classificacao);
        if (competencia != null) {
            try {
                if (!competencia.matches("[0-9]{4}-(0[1-9]|1[0-2])") || competencia.startsWith("0000"))
                    throw new IllegalArgumentException();
                conta.setCompetencia(YearMonth.parse(competencia).atDay(1));
            } catch (RuntimeException exception) { throw new RegraNegocioException("Informe a competência no formato yyyy-MM."); }
        }
        return conta;
    }
}
