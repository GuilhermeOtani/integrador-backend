package com.example.integrador.DTO;

import com.example.integrador.Enum.StatusFinanceiro;
import com.example.integrador.Model.ContaPagar;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Uma única regra para CRUD, dashboard, pagamentos e relatórios. */
public record PosicaoContaPagar(BigDecimal valorPago, BigDecimal saldoRestante,
                               boolean pagamentoParcial, List<String> avisos) {
    public static PosicaoContaPagar calcular(ContaPagar conta, BigDecimal pago, long quantidade) {
        List<String> avisos = new ArrayList<>();
        BigDecimal valor = conta.getValor() == null ? BigDecimal.ZERO : conta.getValor();
        if (conta.getValor() == null || valor.signum() <= 0) avisos.add("VALOR_INVALIDO");
        if (conta.getCompetencia() == null) avisos.add("COMPETENCIA_AUSENTE");
        if (conta.getDataVencimento() == null) avisos.add("VENCIMENTO_AUSENTE");
        if (pago.compareTo(valor) > 0) avisos.add("PAGAMENTO_SUPERIOR_A_CONTA");
        if (pago.signum() < 0) avisos.add("PAGAMENTO_NEGATIVO_LEGADO");
        boolean semComprovante = conta.getStatus() == StatusFinanceiro.PAGO && quantidade == 0;
        if (semComprovante) avisos.add("QUITACAO_SEM_COMPROVANTE");
        BigDecimal saldo = semComprovante ? BigDecimal.ZERO : valor.subtract(pago).max(BigDecimal.ZERO);
        if (conta.getStatus() == StatusFinanceiro.PAGO && saldo.signum() > 0) avisos.add("STATUS_DIVERGENTE");
        return new PosicaoContaPagar(pago, saldo, pago.signum() > 0 && saldo.signum() > 0, List.copyOf(avisos));
    }
}
