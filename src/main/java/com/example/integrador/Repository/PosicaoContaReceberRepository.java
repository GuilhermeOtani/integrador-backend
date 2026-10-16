package com.example.integrador.Repository;

import com.example.integrador.DTO.PosicaoContaReceber;
import com.example.integrador.Model.ContaReceber;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;

@Repository
@Transactional(readOnly = true)
public class PosicaoContaReceberRepository {
    private final RecebimentoRepository recebimentos;
    public PosicaoContaReceberRepository(RecebimentoRepository recebimentos) { this.recebimentos = recebimentos; }
    public Map<Long, PosicaoContaReceber> calcular(List<ContaReceber> contas) {
        Map<Long, Object[]> totais = new HashMap<>();
        if (!contas.isEmpty()) for (Object[] row : recebimentos.totaisPorContas(contas.stream().map(ContaReceber::getId).toList()))
            totais.put((Long) row[0], row);
        Map<Long, PosicaoContaReceber> resultado = new HashMap<>();
        for (var c : contas) {
            Object[] total = totais.get(c.getId());
            resultado.put(c.getId(), PosicaoContaReceber.calcular(c, total == null ? BigDecimal.ZERO : (BigDecimal) total[1],
                    total == null ? 0 : ((Number) total[2]).longValue()));
        }
        return resultado;
    }
}
