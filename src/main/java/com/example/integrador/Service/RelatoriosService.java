package com.example.integrador.Service;

import com.example.integrador.Config.DashboardClockConfig;
import com.example.integrador.DTO.RelatoriosDTO.*;
import com.example.integrador.Enum.ClassificacaoConta;
import com.example.integrador.Exception.*;
import com.example.integrador.Model.*;
import com.example.integrador.Repository.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@PreAuthorize("hasRole('ADMIN')")
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class RelatoriosService {
    private final RelatoriosRepository consultas;
    private final AlunoRepository alunos;
    private final MotoristaRepository motoristas;
    private final PosicaoContaPagarRepository posicoes;
    private final SaldoBaseCaixaService saldoBase;
    private final Clock clock;
    private final PosicaoContaReceberRepository posicoesReceber;
    public RelatoriosService(RelatoriosRepository consultas, AlunoRepository alunos, MotoristaRepository motoristas,
                             PosicaoContaPagarRepository posicoes, SaldoBaseCaixaService saldoBase, Clock clock, PosicaoContaReceberRepository posicoesReceber) {
        this.consultas = consultas; this.alunos = alunos; this.motoristas = motoristas;
        this.posicoes = posicoes; this.saldoBase = saldoBase; this.clock = clock; this.posicoesReceber = posicoesReceber;
    }
    public Inadimplencia inadimplencia(Long alunoId, LocalDate inicio, LocalDate fim) {
        validarPeriodo(inicio, fim);
        if (alunoId != null && !alunos.existsById(alunoId)) throw new RecursoNaoEncontradoException("Aluno não encontrado.");
        var agora = agora();
        List<String> avisos = new ArrayList<>();
        for (Long id : consultas.cobrancasSemVencimento(alunoId)) avisos.add("Conta a receber " + id + ": vencimento ausente; regularize para apurar inadimplência.");
        Map<Long, List<ContaReceber>> grupos = new LinkedHashMap<>();
        var candidatas = consultas.vencidas(agora.toLocalDate(), alunoId, inicio, fim);
        var saldos = posicoesReceber.calcular(candidatas);
        for (ContaReceber c : candidatas) {
            var p = saldos.get(c.getId());
            for (String aviso : p.avisos()) avisos.add("Conta a receber " + c.getId() + ": " + aviso);
            if (p.saldoRestante().signum() > 0) grupos.computeIfAbsent(c.getAluno().getId(), id -> new ArrayList<>()).add(c);
        }
        List<AlunoInadimplente> resultado = new ArrayList<>();
        long quantidade = 0;
        BigDecimal total = BigDecimal.ZERO;
        for (var grupo : grupos.values()) {
            Aluno aluno = grupo.getFirst().getAluno();
            List<Cobranca> cobrancas = new ArrayList<>(); BigDecimal subtotal = BigDecimal.ZERO;
            for (ContaReceber c : grupo) {
                if (c.getValor() == null || c.getValor().signum() <= 0) avisos.add("Conta a receber " + c.getId() + ": valor inválido.");
                var p = saldos.get(c.getId());
                cobrancas.add(new Cobranca(c.getId(), c.getDescricao(), c.getDataVencimento(), c.getValor(), ChronoUnit.DAYS.between(c.getDataVencimento(), agora.toLocalDate()),
                        c.getCompetencia() == null ? null : YearMonth.from(c.getCompetencia()).toString(), p.valorRecebido(), p.saldoRestante(), p.recebimentoParcial(), p.avisos()));
                subtotal = subtotal.add(p.saldoRestante());
            }
            resultado.add(new AlunoInadimplente(aluno.getId(), aluno.getNome(), aluno.getMatricula(), aluno.getTelefone(), aluno.isArquivado(), List.copyOf(cobrancas), subtotal));
            total = total.add(subtotal); quantidade += cobrancas.size();
        }
        return new Inadimplencia(agora.toLocalDate(), agora, alunoId, inicio, fim, List.copyOf(resultado), quantidade, total, List.copyOf(avisos));
    }
    public FluxoCaixa fluxoCaixa(LocalDate inicio, LocalDate fim) {
        if (inicio == null || fim == null) throw new RegraNegocioException("Informe dataInicio e dataFim.");
        validarPeriodo(inicio, fim);
        var base = saldoBase.buscar();
        if (inicio.isBefore(base.dataReferencia())) throw new RegraNegocioException("O período deve começar na data do saldo base ou depois dela.");
        BigDecimal inicial = base.saldo().add(consultas.saldoAnterior(base.dataReferencia(), inicio));
        List<String> avisos = new ArrayList<>();
        for (Long id : consultas.pagamentosSemData()) avisos.add("Pagamento " + id + ": sem data, fora da apuração.");
        for (Long id : consultas.recebimentosSemData()) avisos.add("Recebimento " + id + ": sem data, fora da apuração.");
        for (Long id : consultas.quitacoesSemComprovante()) avisos.add("Conta a pagar " + id + ": quitação sem comprovante; saída não presumida.");
        for (Long id : consultas.quitacoesReceberSemComprovante()) avisos.add("Conta a receber " + id + ": quitação sem comprovante; entrada não presumida.");
        List<Movimento> linhas = new ArrayList<>();
        for (var r : consultas.recebimentos(inicio, fim)) {
            var c = r.getContaReceber();
            linhas.add(new Movimento(r.getId(), c.getId(), r.getDataRecebimento(), TipoMovimento.ENTRADA, c.getDescricao(), c.getAluno().getNome(), decimal(r.getValor()), null));
            if (r.getValor() == null || r.getValor().signum() <= 0) avisos.add("Recebimento " + r.getId() + ": valor inválido.");
        }
        for (var p : consultas.pagamentos(inicio, fim)) {
            var c = p.getContaPagar();
            linhas.add(new Movimento(p.getId(), c == null ? null : c.getId(), p.getDataPagamento(), TipoMovimento.SAIDA,
                    c == null ? "Pagamento sem conta vinculada" : c.getDescricao(), c == null || c.getMotorista() == null ? null : c.getMotorista().getNome(), decimal(p.getValor()), null));
            if (p.getValor() == null || p.getValor().signum() <= 0) avisos.add("Pagamento " + p.getId() + ": valor inválido.");
        }
        linhas.sort(Comparator.comparing(Movimento::data).thenComparing(Movimento::tipo).thenComparing(Movimento::id));
        BigDecimal entradas = BigDecimal.ZERO, saidas = BigDecimal.ZERO, saldo = inicial;
        List<Movimento> movimentos = new ArrayList<>();
        for (var m : linhas) {
            if (m.tipo() == TipoMovimento.ENTRADA) { entradas = entradas.add(m.valor()); saldo = saldo.add(m.valor()); }
            else { saidas = saidas.add(m.valor()); saldo = saldo.subtract(m.valor()); }
            movimentos.add(new Movimento(m.id(), m.contaId(), m.data(), m.tipo(), m.descricao(), m.pessoa(), m.valor(), saldo));
        }
        var agora = agora();
        return new FluxoCaixa(agora.toLocalDate(), agora, inicio, fim, base, inicial, entradas, saidas, saldo, List.copyOf(movimentos), List.copyOf(avisos));
    }
    public Acerto acerto(String mes, Long motoristaId) {
        YearMonth competencia = mes(mes);
        if (motoristaId != null && !motoristas.existsById(motoristaId)) throw new RecursoNaoEncontradoException("Motorista não encontrado.");
        var todas = consultas.contasMotoristas(motoristaId, competencia.atDay(1));
        List<String> avisos = new ArrayList<>();
        for (var c : todas) if (c.getCompetencia() == null) avisos.add("Conta a pagar " + c.getId() + ": competência ausente; regularize o cadastro.");
        var contas = todas.stream().filter(c -> competencia.atDay(1).equals(c.getCompetencia())).toList();
        var saldos = posicoes.calcular(contas);
        Map<Long, List<ContaPagar>> grupos = new LinkedHashMap<>();
        for (var c : contas) grupos.computeIfAbsent(c.getMotorista().getId(), id -> new ArrayList<>()).add(c);
        List<AcertoMotorista> resultado = new ArrayList<>();
        BigDecimal previsto = BigDecimal.ZERO, pago = BigDecimal.ZERO, restante = BigDecimal.ZERO;
        for (var grupo : grupos.values()) {
            List<Lancamento> lancamentos = new ArrayList<>();
            BigDecimal salarios = BigDecimal.ZERO, despesas = BigDecimal.ZERO, pagos = BigDecimal.ZERO, saldo = BigDecimal.ZERO;
            for (var c : grupo) {
                var p = saldos.get(c.getId());
                ClassificacaoConta tipo = c.getClassificacao() == null ? ClassificacaoConta.DESPESA : c.getClassificacao();
                if (tipo == ClassificacaoConta.SALARIO) salarios = salarios.add(decimal(c.getValor())); else despesas = despesas.add(decimal(c.getValor()));
                pagos = pagos.add(p.valorPago()); saldo = saldo.add(p.saldoRestante());
                lancamentos.add(new Lancamento(c.getId(), c.getDescricao(), mes, c.getDataVencimento(), tipo, c.getValor(), p.valorPago(), p.saldoRestante(), p.pagamentoParcial(), p.avisos()));
            }
            var pessoa = grupo.getFirst().getMotorista();
            resultado.add(new AcertoMotorista(pessoa.getId(), pessoa.getNome(), List.copyOf(lancamentos), salarios, despesas, salarios.add(despesas), pagos, saldo));
            previsto = previsto.add(salarios).add(despesas); pago = pago.add(pagos); restante = restante.add(saldo);
        }
        var agora = agora();
        return new Acerto(agora.toLocalDate(), agora, mes, motoristaId, List.copyOf(resultado), previsto, pago, restante, List.copyOf(avisos));
    }
    private OffsetDateTime agora() { return OffsetDateTime.ofInstant(clock.instant(), DashboardClockConfig.ZONA); }
    private BigDecimal decimal(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
    private void validarPeriodo(LocalDate inicio, LocalDate fim) {
        if (inicio != null && (inicio.getYear() < 1 || inicio.getYear() > 9999) || fim != null && (fim.getYear() < 1 || fim.getYear() > 9999))
            throw new RegraNegocioException("Informe datas entre os anos 0001 e 9999.");
        if (inicio != null && fim != null && inicio.isAfter(fim)) throw new RegraNegocioException("A data inicial deve ser anterior ou igual à final.");
    }
    private YearMonth mes(String valor) {
        if (valor == null || !valor.matches("[0-9]{4}-(0[1-9]|1[0-2])") || valor.startsWith("0000")) throw new RegraNegocioException("Informe o mês no formato yyyy-MM.");
        return YearMonth.parse(valor);
    }
}
