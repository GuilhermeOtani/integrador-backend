package com.example.integrador.Service;

import com.example.integrador.Config.DashboardClockConfig;
import com.example.integrador.DTO.DashboardResponse.*;
import com.example.integrador.DTO.ViagemDashboardBase;
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

    public DashboardService(DashboardConsultasRepository consultas, ContaReceberRepository contasReceber,
                            UsuarioRepository usuarios, Clock clock) {
        this.consultas = consultas;
        this.contasReceber = contasReceber;
        this.usuarios = usuarios;
        this.clock = clock;
    }

    @PreAuthorize("hasRole('ALUNO')")
    public AlunoDashboard aluno() {
        Referencia agora = referencia();
        Usuario usuario = usuarioAtual();
        if (!(usuario.getPessoa() instanceof Aluno aluno)) {
            throw new RegraNegocioException("A conta não possui um perfil de aluno");
        }
        var faculdade = aluno.getFaculdade();
        Long faculdadeId = faculdade == null ? null : faculdade.getId();
        TransporteResumo transporte = transporte(consultas.viagensAluno(faculdadeId), faculdadeId, agora.data());
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
        TransporteResumo transporte = transporte(consultas.viagensMotorista(motorista.getId()), null, agora.data());
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

    private TransporteResumo transporte(List<ViagemDashboardBase> viagens, Long faculdadeId, LocalDate hoje) {
        Set<Long> rotaIds = new HashSet<>();
        for (var viagem : viagens) if (viagem.rotaId() != null) rotaIds.add(viagem.rotaId());
        var rotas = consultas.rotas(faculdadeId, rotaIds);
        List<Long> ids = rotas.stream().map(r -> r.getId()).toList();
        Map<Long, Map<Long, PontoResumo>> pontos = new HashMap<>();
        adicionarPontos(pontos, consultas.pontosDaColecao(ids));
        adicionarPontos(pontos, consultas.pontosDaReferencia(ids));
        Comparator<PontoResumo> ordemPontos = Comparator.comparing(PontoResumo::ordemParada,
                Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(PontoResumo::id);
        Map<Long, RotaResumo> mapaRotas = new HashMap<>();
        for (var rota : rotas) {
            List<PontoResumo> lista = pontos.getOrDefault(rota.getId(), Map.of()).values().stream().sorted(ordemPontos).toList();
            mapaRotas.put(rota.getId(), new RotaResumo(rota.getId(), rota.getNome(), rota.getDescricao(), lista));
        }
        Map<Long, List<FaculdadeResumo>> faculdades = new HashMap<>();
        for (Object[] row : consultas.faculdadesDasViagens(viagens.stream().map(ViagemDashboardBase::id).toList())) {
            faculdades.computeIfAbsent((Long) row[0], id -> new ArrayList<>())
                    .add(new FaculdadeResumo((Long) row[1], (String) row[2], (String) row[3]));
        }
        List<ViagemResumo> agenda = new ArrayList<>();
        List<ViagemResumo> semGrade = new ArrayList<>();
        for (var viagem : viagens) {
            LocalDate ocorrencia = ocorrenciaSemanal(hoje, viagem.diaSemana());
            List<String> avisos = new ArrayList<>();
            if (ocorrencia == null) avisos.add("Programação não definida. A administração precisa cadastrar a grade/dia da semana.");
            boolean disponivel = viagem.onibusId() != null && viagem.statusOnibus() == StatusOnibus.OPERANDO;
            if (!disponivel) avisos.add(viagem.onibusId() == null ? "Ônibus não definido."
                    : "Ônibus indisponível ou com estado não informado. Confirme a escala com a administração.");
            if (viagem.motoristaId() == null) avisos.add("Motorista não definido.");
            if (viagem.rotaId() == null) avisos.add("Rota não definida.");
            ViagemResumo resumo = new ViagemResumo(viagem.id(),
                    viagem.gradeId() == null ? null : new GradeResumo(viagem.gradeId(), viagem.diaSemana(), viagem.gradeDescricao()),
                    ocorrencia, mapaRotas.get(viagem.rotaId()),
                    viagem.onibusId() == null ? null : new OnibusResumo(viagem.onibusId(), viagem.numeroIdentificacao(),
                            viagem.placa(), viagem.modelo(), viagem.capacidade(), viagem.statusOnibus()),
                    viagem.motoristaId() == null ? null : new PessoaResumo(viagem.motoristaId(), viagem.motoristaNome()),
                    List.copyOf(faculdades.getOrDefault(viagem.id(), List.of())), disponivel, List.copyOf(avisos));
            if (ocorrencia == null) semGrade.add(resumo); else agenda.add(resumo);
        }
        agenda.sort(Comparator.comparing(ViagemResumo::dataOcorrencia).thenComparing(ViagemResumo::id));
        semGrade.sort(Comparator.comparing(ViagemResumo::id));
        var rotasOrdenadas = mapaRotas.values().stream().sorted(Comparator.comparing(RotaResumo::nome,
                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)).thenComparing(RotaResumo::id)).toList();
        return new TransporteResumo(agenda.stream().filter(v -> hoje.equals(v.dataOcorrencia())).toList(),
                List.copyOf(agenda), List.copyOf(semGrade), rotasOrdenadas);
    }

    private void adicionarPontos(Map<Long, Map<Long, PontoResumo>> pontos, List<Object[]> rows) {
        for (Object[] row : rows) {
            var ponto = new PontoResumo((Long) row[1], (String) row[2], (Integer) row[3]);
            pontos.computeIfAbsent((Long) row[0], id -> new HashMap<>()).put(ponto.id(), ponto);
        }
    }

    private FinanceiroAluno financeiroAluno(Aluno aluno, LocalDate hoje) {
        var contas = contasReceber.findAllByAlunoIdOrderByDataVencimentoDesc(aluno.getId());
        var abertas = contas.stream().filter(c -> c.getStatus() != StatusFinanceiro.PAGO).toList();
        var vencidas = abertas.stream().filter(c -> c.getDataVencimento() != null && c.getDataVencimento().isBefore(hoje)).toList();
        Comparator<ContaReceber> ordem = Comparator.comparing(ContaReceber::getDataVencimento,
                Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(ContaReceber::getId);
        ContaReceber principal = abertas.stream().min(ordem).orElseGet(() -> contas.stream()
                .max(Comparator.comparing(ContaReceber::getDataVencimento,
                        Comparator.nullsFirst(Comparator.naturalOrder())).thenComparing(ContaReceber::getId)).orElse(null));
        return new FinanceiroAluno(aluno.getMensalidade(), principal == null ? null : new CobrancaResumo(
                principal.getId(), principal.getDescricao(), principal.getValor(), principal.getDataVencimento(),
                principal.getStatus(), situacao(principal.getStatus(), principal.getDataVencimento(), hoje),
                principal.getStatus() == StatusFinanceiro.PAGO || principal.getDataVencimento() == null ? null
                        : ChronoUnit.DAYS.between(hoje, principal.getDataVencimento())), total(abertas), total(vencidas));
    }

    private TotalFinanceiro total(List<ContaReceber> contas) {
        return new TotalFinanceiro(contas.size(), contas.stream().map(ContaReceber::getValor).filter(Objects::nonNull)
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

    private LocalDate ocorrenciaSemanal(LocalDate hoje, DiaSemana dia) {
        if (dia == null) return null;
        DayOfWeek diaDaGrade = switch (dia) {
            case SEGUNDA -> DayOfWeek.MONDAY;
            case TERCA -> DayOfWeek.TUESDAY;
            case QUARTA -> DayOfWeek.WEDNESDAY;
            case QUINTA -> DayOfWeek.THURSDAY;
            case SEXTA -> DayOfWeek.FRIDAY;
        };
        int deslocamento = Math.floorMod(diaDaGrade.getValue() - hoje.getDayOfWeek().getValue(), 7);
        return hoje.plusDays(deslocamento);
    }

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
