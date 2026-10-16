package com.example.integrador.DTO;

import com.example.integrador.Enum.*;
import com.example.integrador.Model.ContaPagar;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

public record ContaPagarResponse(Long id, String descricao, BigDecimal valor, LocalDate dataVencimento,
        StatusFinanceiro status, PessoaResumo motorista, String competencia, ClassificacaoConta classificacao,
        BigDecimal valorPago, BigDecimal saldoRestante, boolean pagamentoParcial, List<String> avisos) {
    public record PessoaResumo(Long id, String nome, TipoPessoa tipoPessoa) {}
    public static ContaPagarResponse from(ContaPagar c, PosicaoContaPagar p) {
        var pessoa = c.getMotorista();
        return new ContaPagarResponse(c.getId(), c.getDescricao(), c.getValor(), c.getDataVencimento(), c.getStatus(),
                pessoa == null ? null : new PessoaResumo(pessoa.getId(), pessoa.getNome(), pessoa.getTipoPessoa()),
                c.getCompetencia() == null ? null : YearMonth.from(c.getCompetencia()).toString(), c.getClassificacao(),
                p.valorPago(), p.saldoRestante(), p.pagamentoParcial(), p.avisos());
    }
}
