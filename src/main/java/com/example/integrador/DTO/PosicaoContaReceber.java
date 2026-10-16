package com.example.integrador.DTO;

import com.example.integrador.Enum.StatusFinanceiro;
import com.example.integrador.Model.ContaReceber;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public record PosicaoContaReceber(BigDecimal valorRecebido, BigDecimal saldoRestante,
                                 boolean recebimentoParcial, List<String> avisos) {
    public static PosicaoContaReceber calcular(ContaReceber conta, BigDecimal recebido, long quantidade) {
        List<String> avisos = new ArrayList<>();
        BigDecimal valor = conta.getValor() == null ? BigDecimal.ZERO : conta.getValor();
        if (conta.getValor() == null || valor.signum() <= 0) avisos.add("VALOR_INVALIDO");
        if (conta.getCompetencia() == null) avisos.add("COMPETENCIA_AUSENTE");
        if (conta.getDataVencimento() == null) avisos.add("VENCIMENTO_AUSENTE");
        if (recebido.compareTo(valor) > 0) avisos.add("RECEBIMENTO_SUPERIOR_A_CONTA");
        if (recebido.signum() < 0) avisos.add("RECEBIMENTO_NEGATIVO_LEGADO");
        boolean semComprovante = conta.getStatus() == StatusFinanceiro.PAGO && quantidade == 0;
        if (semComprovante) avisos.add("QUITACAO_SEM_COMPROVANTE");
        BigDecimal saldo = semComprovante ? BigDecimal.ZERO : valor.subtract(recebido).max(BigDecimal.ZERO);
        if (conta.getStatus() == StatusFinanceiro.PAGO && saldo.signum() > 0) avisos.add("STATUS_DIVERGENTE");
        return new PosicaoContaReceber(recebido, saldo, recebido.signum() > 0 && saldo.signum() > 0, List.copyOf(avisos));
    }
}
