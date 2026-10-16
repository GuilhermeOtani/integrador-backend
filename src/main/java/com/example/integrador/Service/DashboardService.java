package com.example.integrador.Service;

import com.example.integrador.Config.DashboardClockConfig;
import com.example.integrador.DTO.DashboardResponse.*;
import com.example.integrador.Enum.*;
import com.example.integrador.Exception.RegraNegocioException;
import com.example.integrador.Model.Aluno;
import com.example.integrador.Model.ContaReceber;
import com.example.integrador.Model.Motorista;
import com.example.integrador.Model.Usuario;
import com.example.integrador.Repository.ContaReceberRepository;
import com.example.integrador.Repository.DashboardConsultasRepository;
import com.example.integrador.Repository.DashboardConsultasRepository.TipoConta;
import com.example.integrador.Repository.UsuarioRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class DashboardService {
    private final DashboardConsultasRepository consultas;
    private final ContaReceberRepository contasReceber;
    private final UsuarioRepository usuarios;
    private final Clock clock;
    private final com.example.integrador.Repository.PosicaoContaReceberRepository posicoesReceber;
    private final AgendaTransporteService agenda;

    public DashboardService(DashboardConsultasRepository consultas, ContaReceberRepository contasReceber,
                            UsuarioRepository usuarios, Clock clock, AgendaTransporteService agenda,
                            com.example.integrador.Repository.PosicaoContaReceberRepository posicoesReceber) {
        this.consultas = consultas;
        this.contasReceber = contasReceber;
        this.usuarios = usuarios;
        this.clock = clock;
        this.agenda = agenda;
        this.posicoesReceber = posicoesReceber;
    }

    @PreAuthorize("hasRole('ALUNO')")
    public AlunoDashboard aluno() {
        Referencia agora = referencia();
        Usuario usuario = usuarioAtual();
        if (!(usuario.getPessoa() instanceof Aluno aluno)) {
            throw new RegraNegocioException("A conta não possui um perfil de aluno");
        }
        var faculdade = aluno.getFaculdade();
        TransporteResumo transporte = agenda.aluno(aluno, agora.data());
        List<String> avisos = new ArrayList<>();
        if (faculdade == null) avisos.add("Sua faculdade não foi cadastrada. Procure a administração.");
        if (transporte.agendaSemanal().isEmpty()) avisos.add("Não há viagens com programação semanal para sua faculdade.");
        if (transporte.rotas().isEmpty()) avisos.add("Nenhuma rota foi cadastrada para sua faculdade.");
        return new AlunoDashboard(agora.data(), agora.geradoEm(),
                new AlunoResumo(aluno.getId(), aluno.getNome(), aluno.getMatricula(), aluno.getStatusMatricula()),
                faculdade == null ? null : new FaculdadeResumo(faculdade.getId(), faculdade.getNome(), faculdade.getEndereco()),
                transporte, financeiroAluno(aluno, agora.data()), List.copyOf(avisos));
    }

    @PreAuthorize("hasRole('MOTORISTA')")
    public MotoristaDashboard motorista() {
        Referencia agora = referencia();
        Usuario usuario = usuarioAtual();
        if (!(usuario.getPessoa() instanceof Motorista motorista)) {
            throw new RegraNegocioException("A conta não possui um perfil de motorista");
        }
        TransporteResumo transporte = agenda.motorista(motorista, agora.data());
        List<String> avisos = transporte.agendaSemanal().isEmpty()
                ? List.of("Nenhuma viagem com programação semanal foi atribuída a você.") : List.of();
        return new MotoristaDashboard(agora.data(), agora.geradoEm(),
                new PessoaResumo(motorista.getId(), motorista.getNome()), transporte, avisos);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public AdminDashboard admin(String mesInformado) {
        Referencia agora = referencia();
        YearMonth mes = parseMes(mesInformado, agora.data());
        LocalDate inicio = mes.minusMonths(5).atDay(1);
        LocalDate fim = mes.plusMonths(1).atDay(1);
        var recebimentos = consultas.recebimentos(inicio, fim);
        var pagamentos = consultas.pagamentos(inicio, fim);
        List<MovimentoMensal> serie = new ArrayList<>();
        for (int i = 5; i >= 0; i--) {
            YearMonth item = mes.minusMonths(i);
            BigDecimal recebido = recebimentos.getOrDefault(item, BigDecimal.ZERO);
            BigDecimal pago = pagamentos.getOrDefault(item, BigDecimal.ZERO);
            serie.add(new MovimentoMensal(item.toString(), recebido, pago, recebido.subtract(pago)));
        }
        MovimentoMensal atual = serie.getLast();
        var financeiro = new FinanceiroAdmin(atual.recebido(), atual.pago(), atual.saldoMovimentacoes(),
                resumoContas(consultas.resumoContas(TipoConta.RECEBER, agora.data())),
                resumoContas(consultas.resumoContas(TipoConta.PAGAR, agora.data())), List.copyOf(serie),
                pendencias(TipoConta.RECEBER, agora.data()), pendencias(TipoConta.PAGAR, agora.data()));
        return new AdminDashboard(agora.data(), agora.geradoEm(), mes.toString(), financeiro, operacao(agora.data()));
    }

    private FinanceiroAluno financeiroAluno(Aluno aluno, LocalDate hoje) {
        var contas = contasReceber.findAllByAlunoIdOrderByDataVencimentoDesc(aluno.getId());
        var saldos = posicoesReceber.calcular(contas);
        var abertas = contas.stream().filter(c -> saldos.get(c.getId()).saldoRestante().signum() > 0
                || c.getValor() == null && c.getStatus() != StatusFinanceiro.PAGO).toList();
        var vencidas = abertas.stream().filter(c -> c.getDataVencimento() != null && c.getDataVencimento().isBefore(hoje)).toList();
        Comparator<ContaReceber> ordem = Comparator.comparing(ContaReceber::getDataVencimento,
                Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(ContaReceber::getId);
        ContaReceber principal = abertas.stream().min(ordem).orElseGet(() -> contas.stream()
                .max(Comparator.comparing(ContaReceber::getDataVencimento,
                        Comparator.nullsFirst(Comparator.naturalOrder())).thenComparing(ContaReceber::getId)).orElse(null));
        CobrancaResumo resumo = null;
        if (principal != null) {
            var p = saldos.get(principal.getId());
            StatusFinanceiro status = principal.getStatus();
            if (p.saldoRestante().signum() > 0 && status == StatusFinanceiro.PAGO) status = StatusFinanceiro.PENDENTE;
            resumo = new CobrancaResumo(principal.getId(), principal.getDescricao(), principal.getValor(), principal.getDataVencimento(),
                    status, situacao(status, principal.getDataVencimento(), hoje),
                    status == StatusFinanceiro.PAGO || principal.getDataVencimento() == null ? null : ChronoUnit.DAYS.between(hoje, principal.getDataVencimento()),
                    principal.getCompetencia() == null ? null : YearMonth.from(principal.getCompetencia()).toString(),
                    p.valorRecebido(), p.saldoRestante(), p.recebimentoParcial(), p.avisos());
        }
        return new FinanceiroAluno(aluno.getMensalidade(), resumo, total(abertas, saldos), total(vencidas, saldos));
    }

    private TotalFinanceiro total(List<ContaReceber> contas, Map<Long, com.example.integrador.DTO.PosicaoContaReceber> saldos) {
        return new TotalFinanceiro(contas.size(), contas.stream().map(c -> saldos.get(c.getId()).saldoRestante())
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private SituacaoCobranca situacao(StatusFinanceiro status, LocalDate vencimento, LocalDate hoje) {
        if (status == StatusFinanceiro.PAGO) return SituacaoCobranca.PAGO;
        if (vencimento == null) return SituacaoCobranca.SEM_VENCIMENTO;
        if (vencimento.isBefore(hoje)) return SituacaoCobranca.VENCIDA;
        return vencimento.equals(hoje) ? SituacaoCobranca.VENCE_HOJE : SituacaoCobranca.A_VENCER;
    }

    private ResumoContas resumoContas(Object[] row) {
        return new ResumoContas(new TotalFinanceiro(numero(row[0]), decimal(row[1])),
                new TotalFinanceiro(numero(row[2]), decimal(row[3])), new TotalFinanceiro(numero(row[4]), decimal(row[5])));
    }

    private List<PendenciaFinanceira> pendencias(TipoConta tipo, LocalDate hoje) {
        return consultas.pendencias(tipo).stream().map(row -> new PendenciaFinanceira((Long) row[0], (String) row[1],
                row[2] == null ? null : decimal(row[2]), (LocalDate) row[3], (StatusFinanceiro) row[4],
                situacao((StatusFinanceiro) row[4], (LocalDate) row[3], hoje), (String) row[5])).toList();
    }

    private OperacaoResumo operacao(LocalDate hoje) {
        Map<Object, Long> alunos = contagens(consultas.alunosPorStatus());
        Map<Object, Long> onibus = contagens(consultas.onibusPorStatus());
        long abertos = 0, emAndamento = 0, alta = 0;
        for (Object[] row : consultas.chamadosPorStatus()) {
            if (row[0] == StatusChamado.ABERTO) abertos = numero(row[1]);
            if (row[0] == StatusChamado.EM_ANDAMENTO) emAndamento = numero(row[1]);
            if (row[0] == StatusChamado.ABERTO || row[0] == StatusChamado.EM_ANDAMENTO) alta += numero(row[2]);
        }
        var pendentes = consultas.chamadosPendentes().stream().map(row -> new ChamadoPendente(
                (Long) row[0], (String) row[1], (LocalDate) row[2], (NivelUrgencia) row[3], (StatusChamado) row[4],
                (Long) row[5], (String) row[6], (String) row[7], (String) row[8])).toList();
        return new OperacaoResumo(new AlunosIndicadores(soma(alunos), count(alunos, StatusMatricula.ATIV0),
                count(alunos, StatusMatricula.INATIV0), count(alunos, StatusMatricula.PENDENTE), count(alunos, null)),
                new OnibusIndicadores(soma(onibus), count(onibus, StatusOnibus.OPERANDO), count(onibus, StatusOnibus.MANUTENCAO),
                        count(onibus, StatusOnibus.QUEBRADO), count(onibus, StatusOnibus.INATIVO), count(onibus, null)),
                consultas.viagensHoje(diaSemana(hoje.getDayOfWeek())), consultas.viagensSemProgramacao(),
                new ChamadosIndicadores(abertos, emAndamento, alta), pendentes);
    }

    private Map<Object, Long> contagens(List<Object[]> rows) {
        Map<Object, Long> resultado = new HashMap<>();
        for (Object[] row : rows) resultado.put(row[0], numero(row[1]));
        return resultado;
    }
    private long count(Map<Object, Long> dados, Object chave) { return dados.getOrDefault(chave, 0L); }
    private long soma(Map<Object, Long> dados) { return dados.values().stream().mapToLong(Long::longValue).sum(); }
    private long numero(Object valor) { return valor == null ? 0 : ((Number) valor).longValue(); }
    private BigDecimal decimal(Object valor) { return DashboardConsultasRepository.decimal(valor); }

    private DiaSemana diaSemana(DayOfWeek dia) {
        return switch (dia) {
            case MONDAY -> DiaSemana.SEGUNDA;
            case TUESDAY -> DiaSemana.TERCA;
            case WEDNESDAY -> DiaSemana.QUARTA;
            case THURSDAY -> DiaSemana.QUINTA;
            case FRIDAY -> DiaSemana.SEXTA;
            default -> null;
        };
    }

    private YearMonth parseMes(String valor, LocalDate hoje) {
        if (valor == null) return YearMonth.from(hoje);
        if (!valor.matches("[0-9]{4}-(0[1-9]|1[0-2])")) throw new RegraNegocioException("Informe o mês no formato yyyy-MM");
        YearMonth mes = YearMonth.parse(valor);
        if (mes.getYear() < 1) throw new RegraNegocioException("Informe um ano entre 0001 e 9999");
        return mes;
    }

    private Referencia referencia() {
        var agora = OffsetDateTime.ofInstant(clock.instant(), DashboardClockConfig.ZONA);
        return new Referencia(agora.toLocalDate(), agora);
    }
    private record Referencia(LocalDate data, OffsetDateTime geradoEm) {}

    private Usuario usuarioAtual() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwt)) {
            throw new AuthenticationCredentialsNotFoundException("JWT obrigatório");
        }
        return usuarios.findById(Long.valueOf(jwt.getToken().getSubject()))
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("Conta inexistente"));
    }
}
