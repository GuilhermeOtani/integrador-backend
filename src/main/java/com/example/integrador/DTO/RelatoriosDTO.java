package com.example.integrador.DTO;

import com.example.integrador.Enum.ClassificacaoConta;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;

public final class RelatoriosDTO {
    private RelatoriosDTO() {}
    public record Cobranca(Long contaId, String descricao, LocalDate vencimento, BigDecimal valor, long diasAtraso,
            String competencia, BigDecimal valorRecebido, BigDecimal saldoRestante, boolean recebimentoParcial,
            List<String> avisos) {}
    public record AlunoInadimplente(Long alunoId, String nome, String matricula, String telefone, boolean arquivado,
                                    List<Cobranca> cobrancas, BigDecimal total) {}
    public record Inadimplencia(LocalDate dataReferencia, OffsetDateTime geradoEm, Long alunoId,
            LocalDate vencimentoInicio, LocalDate vencimentoFim, List<AlunoInadimplente> alunos,
            long quantidadeCobrancas, BigDecimal totalGeral, List<String> avisos) {}
    public enum TipoMovimento { ENTRADA, SAIDA }
    public record Movimento(Long id, Long contaId, LocalDate data, TipoMovimento tipo, String descricao,
                            String pessoa, BigDecimal valor, BigDecimal saldoAcumulado) {}
    public record FluxoCaixa(LocalDate dataReferencia, OffsetDateTime geradoEm, LocalDate dataInicio, LocalDate dataFim,
            SaldoBaseDTO saldoBase, BigDecimal saldoInicial, BigDecimal totalEntradas, BigDecimal totalSaidas,
            BigDecimal saldoFinal, List<Movimento> movimentos, List<String> avisos) {}
    public record Lancamento(Long contaId, String descricao, String competencia, LocalDate vencimento,
            ClassificacaoConta classificacao, BigDecimal valor, BigDecimal valorPago, BigDecimal saldoRestante,
            boolean pagamentoParcial, List<String> avisos) {}
    public record AcertoMotorista(Long motoristaId, String nome, List<Lancamento> lancamentos,
            BigDecimal totalSalarios, BigDecimal totalDespesas, BigDecimal totalPrevisto, BigDecimal totalPago,
            BigDecimal saldoAPagar) {}
    public record Acerto(LocalDate dataReferencia, OffsetDateTime geradoEm, String mes, Long motoristaId,
            List<AcertoMotorista> motoristas, BigDecimal totalPrevisto, BigDecimal totalPago,
            BigDecimal saldoAPagar, List<String> avisos) {}
}
