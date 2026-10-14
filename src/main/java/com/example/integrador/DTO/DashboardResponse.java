package com.example.integrador.DTO;

import com.example.integrador.Enum.DiaSemana;
import com.example.integrador.Enum.NivelUrgencia;
import com.example.integrador.Enum.StatusChamado;
import com.example.integrador.Enum.StatusFinanceiro;
import com.example.integrador.Enum.StatusMatricula;
import com.example.integrador.Enum.StatusOnibus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** Contratos de leitura; nenhuma entidade JPA ou dado de credenciais é serializado. */
public final class DashboardResponse {
    private DashboardResponse() {}

    public record AlunoDashboard(LocalDate dataReferencia, OffsetDateTime geradoEm,
            AlunoResumo aluno, FaculdadeResumo faculdade, TransporteResumo transporte,
            FinanceiroAluno financeiro, List<String> avisos) {}

    public record MotoristaDashboard(LocalDate dataReferencia, OffsetDateTime geradoEm,
            PessoaResumo motorista, TransporteResumo transporte, List<String> avisos) {}

    public record AdminDashboard(LocalDate dataReferencia, OffsetDateTime geradoEm,
            String mes, FinanceiroAdmin financeiro, OperacaoResumo operacao) {}

    public record AlunoResumo(Long pessoaId, String nome, String matricula,
                               StatusMatricula statusMatricula) {}
    public record PessoaResumo(Long pessoaId, String nome) {}
    public record FaculdadeResumo(Long id, String nome, String endereco) {}
    public record PontoResumo(Long id, String descricao, Integer ordemParada) {}
    public record RotaResumo(Long id, String nome, String descricao, List<PontoResumo> pontosEmbarque) {}
    public record GradeResumo(Long id, DiaSemana diaSemana, String descricao) {}
    public record OnibusResumo(Long id, String numeroIdentificacao, String placa,
            String modelo, Integer capacidade, StatusOnibus statusOnibus) {}

    public record ViagemResumo(Long id, GradeResumo grade, LocalDate dataOcorrencia,
            RotaResumo rota, OnibusResumo onibus, PessoaResumo motorista,
            List<FaculdadeResumo> faculdades, boolean onibusDisponivel, List<String> avisos) {}

    public record TransporteResumo(List<ViagemResumo> viagensHoje, List<ViagemResumo> agendaSemanal,
            List<ViagemResumo> programacaoSemGrade, List<RotaResumo> rotas) {}

    public enum SituacaoCobranca { PAGO, VENCIDA, VENCE_HOJE, A_VENCER, SEM_VENCIMENTO }
    public record CobrancaResumo(Long id, String descricao, BigDecimal valor,
            LocalDate dataVencimento, StatusFinanceiro status, SituacaoCobranca situacao,
            Long diasParaVencimento) {}
    public record TotalFinanceiro(long quantidade, BigDecimal valor) {}
    public record FinanceiroAluno(BigDecimal mensalidadeCadastro, CobrancaResumo cobrancaPrincipal,
            TotalFinanceiro emAberto, TotalFinanceiro vencidas) {}

    public record ResumoContas(TotalFinanceiro emAberto, TotalFinanceiro vencidas,
                              TotalFinanceiro vencemEmSeteDias) {}
    public record MovimentoMensal(String mes, BigDecimal recebido, BigDecimal pago,
                                  BigDecimal saldoMovimentacoes) {}
    public record PendenciaFinanceira(Long id, String descricao, BigDecimal valor,
            LocalDate dataVencimento, StatusFinanceiro status, SituacaoCobranca situacao,
            String nomePessoa) {}
    public record FinanceiroAdmin(BigDecimal recebidoNoMes, BigDecimal pagoNoMes,
            BigDecimal saldoMovimentacoes, ResumoContas contasReceber, ResumoContas contasPagar,
            List<MovimentoMensal> serieMensal, List<PendenciaFinanceira> pendenciasReceber,
            List<PendenciaFinanceira> pendenciasPagar) {}

    public record AlunosIndicadores(long total, long ativos, long inativos, long pendentes, long semStatus) {}
    public record OnibusIndicadores(long total, long operando, long manutencao, long quebrado,
                                    long inativo, long semStatus) {}
    public record ChamadosIndicadores(long abertos, long emAndamento, long prioridadeAlta) {}
    public record ChamadoPendente(Long id, String descricao, LocalDate dataAbertura,
            NivelUrgencia urgencia, StatusChamado status, Long onibusId,
            String numeroIdentificacao, String placa, String solicitanteNome) {}
    public record OperacaoResumo(AlunosIndicadores alunos, OnibusIndicadores onibus,
            long viagensHoje, long viagensSemProgramacao, ChamadosIndicadores chamados,
            List<ChamadoPendente> chamadosPendentes) {}
}
