package com.example.integrador.Repository;

import com.example.integrador.DTO.PosicaoContaPagar;
import com.example.integrador.Model.ContaPagar;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;

@Repository
@Transactional(readOnly = true)
public class PosicaoContaPagarRepository {
    private final PagamentoRepository pagamentos;
    public PosicaoContaPagarRepository(PagamentoRepository pagamentos) { this.pagamentos = pagamentos; }
    public Map<Long, PosicaoContaPagar> calcular(List<ContaPagar> contas) {
        Map<Long, Object[]> totais = new HashMap<>();
        if (!contas.isEmpty()) for (Object[] row : pagamentos.totaisPorContas(contas.stream().map(ContaPagar::getId).toList()))
            totais.put((Long) row[0], row);
        Map<Long, PosicaoContaPagar> resultado = new HashMap<>();
        for (ContaPagar conta : contas) {
            Object[] total = totais.get(conta.getId());
            resultado.put(conta.getId(), PosicaoContaPagar.calcular(conta,
                    total == null ? BigDecimal.ZERO : (BigDecimal) total[1], total == null ? 0 : ((Number) total[2]).longValue()));
        }
        return resultado;
    }
}
