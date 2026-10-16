package com.example.integrador;

import com.example.integrador.DTO.*;
import com.example.integrador.DTO.DashboardResponse.*;
import com.example.integrador.Enum.*;
import com.example.integrador.Model.*;
import com.example.integrador.Repository.*;
import com.example.integrador.Service.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DashboardIntegrationTests {
    private static final LocalDate HOJE = LocalDate.of(2026, 10, 12); // Segunda-feira.
    @Autowired MockMvc mvc;
    @Autowired DashboardService dashboard;
    @Autowired UsuarioService usuarios;
    @Autowired AuthService auth;
    @Autowired JwtDecoder decoder;
    @Autowired JwtAuthenticationConverter converter;
    @Autowired EntityManager em;
    @Autowired ContaReceberRepository contas;
    @MockitoBean Clock dashboardClock;
    private Faculdade faculdade;
    private Faculdade outraFaculdade;
    private Aluno aluno;
    private Aluno outroAluno;
    private Motorista motorista;
    private Motorista outroMotorista;
    private Onibus onibus;
    private String alunoToken, outroAlunoToken, motoristaToken, outroMotoristaToken, adminToken;

    @BeforeEach
    void preparar() {
        SecurityContextHolder.clearContext();
        when(dashboardClock.instant()).thenReturn(Instant.parse("2026-10-12T15:00:00Z"));
        when(dashboardClock.getZone()).thenReturn(ZoneId.of("America/Sao_Paulo"));
        usuarios.criarAdmin(new CriarAdminRequest("Admin Dashboard", "admin@dashboard.test", "SenhaTeste123"));
        adminToken = token("admin@dashboard.test");
        faculdade = faculdade("Faculdade do aluno");
        outraFaculdade = faculdade("Outra Faculdade");
        aluno = aluno("Aluno A", faculdade, "aluno@dashboard.test");
        outroAluno = aluno("Aluno B", outraFaculdade, "outro@dashboard.test");
        alunoToken = token("aluno@dashboard.test"); outroAlunoToken = token("outro@dashboard.test");
        motorista = motorista("Motorista A", "motorista@dashboard.test");
        outroMotorista = motorista("Motorista B", "outromotorista@dashboard.test");
        motoristaToken = token("motorista@dashboard.test"); outroMotoristaToken = token("outromotorista@dashboard.test");
        onibus = new Onibus();
        onibus.setNumeroIdentificacao("01"); onibus.setPlaca("ABC1234"); onibus.setModelo("Modelo do ônibus");
        onibus.setCapacidade(40); onibus.setStatusOnibus(StatusOnibus.OPERANDO); onibus.setFotoUrl("foto-que-nao-deve-ser-exposta");
        em.persist(onibus);
    }

    @AfterEach
    void limparContexto() { SecurityContextHolder.clearContext(); }

    @Test
    void alunoVeDestinosExplicitosRotasRelacionadasEPontosDeduplicados() throws Exception {
        Rota compartilhada = rota("Rota compartilhada", outraFaculdade);
        PontoEmbarque segundo = ponto("Segundo", 2, compartilhada);
        PontoEmbarque primeiro = ponto("Primeiro", 1, null);
        PontoEmbarque semOrdem = ponto("Sem ordem", null, compartilhada);
        compartilhada.setPontosEmbarque(new ArrayList<>(List.of(segundo, primeiro)));
        Rota semViagem = rota("Rota sem viagem", faculdade);
        Rota naoAtende = rota("Rota com destino diferente", faculdade);
        Viagem elegivel = viagem(compartilhada, DiaSemana.SEGUNDA, motorista, LocalDate.of(2006, 1, 1), faculdade, outraFaculdade);
        viagem(naoAtende, DiaSemana.SEGUNDA, outroMotorista, null, outraFaculdade);
        autenticar(alunoToken);
        AlunoDashboard resposta = dashboard.aluno();
        assertThat(resposta.transporte().agendaSemanal()).extracting(ViagemResumo::id).containsExactly(elegivel.getId());
        assertThat(resposta.transporte().rotas()).extracting(RotaResumo::id)
                .contains(compartilhada.getId(), semViagem.getId(), naoAtende.getId());
        assertThat(resposta.transporte().agendaSemanal().getFirst().rota().pontosEmbarque())
                .extracting(PontoResumo::id).containsExactly(primeiro.getId(), segundo.getId(), semOrdem.getId());
        assertThat(resposta.transporte().agendaSemanal().getFirst().faculdades()).hasSize(2);
        mvc.perform(get("/dashboard/aluno").param("pessoaId", outroAluno.getId().toString())
                        .param("faculdadeId", outraFaculdade.getId().toString()).header("Authorization", alunoToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.aluno.pessoaId").value(aluno.getId()))
                .andExpect(jsonPath("$.faculdade.id").value(faculdade.getId()))
                .andExpect(jsonPath("$.transporte.agendaSemanal.length()").value(1))
                .andExpect(jsonPath("$.aluno.cpfCnpj").doesNotExist())
                .andExpect(jsonPath("$.transporte.agendaSemanal[0].onibus.fotoUrl").doesNotExist())
                .andExpect(jsonPath("$.transporte.agendaSemanal[0].motorista.salario").doesNotExist())
                .andExpect(jsonPath("$.transporte.agendaSemanal[0].motorista.cnh").doesNotExist());
        autenticar(outroAlunoToken);
        assertThat(dashboard.aluno().transporte().agendaSemanal()).hasSize(2);
    }

    @Test
    void motoristaVeSomenteSuasAtribuicoesComInformacoesCompletas() throws Exception {
        Viagem minha = viagem(rota("Rota do motorista", faculdade), DiaSemana.TERCA, motorista, null, faculdade);
        viagem(rota("Rota alheia", outraFaculdade), DiaSemana.SEGUNDA, outroMotorista, null, outraFaculdade);
        mvc.perform(get("/dashboard/motorista").param("motoristaId", outroMotorista.getId().toString())
                        .header("Authorization", motoristaToken)).andExpect(status().isOk())
                .andExpect(jsonPath("$.transporte.agendaSemanal.length()").value(1))
                .andExpect(jsonPath("$.transporte.agendaSemanal[0].id").value(minha.getId()))
                .andExpect(jsonPath("$.transporte.agendaSemanal[0].onibus.placa").value("ABC1234"))
                .andExpect(jsonPath("$.transporte.agendaSemanal[0].onibus.capacidade").value(40))
                .andExpect(jsonPath("$.transporte.agendaSemanal[0].faculdades[0].nome").value(faculdade.getNome()))
                .andExpect(jsonPath("$.transporte.agendaSemanal[0].dataOcorrencia").value("2026-10-13"));
        autenticar(outroMotoristaToken);
        assertThat(dashboard.motorista().transporte().agendaSemanal()).hasSize(1)
                .allSatisfy(v -> assertThat(v.motorista().pessoaId()).isEqualTo(outroMotorista.getId()));
    }

    @Test
    void agendaIgnoraTodasDatasLegadasESeparaViagensSemProgramacao() {
        Rota rota = rota("Agenda", faculdade);
        Viagem antiga = viagem(rota, DiaSemana.SEGUNDA, motorista, LocalDate.of(2006, 8, 12), faculdade);
        Viagem futura = viagem(rota, DiaSemana.SEGUNDA, motorista, HOJE.plusYears(20), faculdade);
        Viagem semData = viagem(rota, DiaSemana.SEGUNDA, motorista, null, faculdade);
        Viagem terca = viagem(rota, DiaSemana.TERCA, motorista, HOJE.minusYears(4), faculdade);
        Viagem semGrade = viagem(rota, null, motorista, HOJE, faculdade);
        Viagem gradeSemDia = viagem(rota, null, motorista, HOJE.plusDays(2), faculdade);
        GradeDiaria indefinida = new GradeDiaria(); em.persist(indefinida); gradeSemDia.setGradeDiaria(indefinida);
        autenticar(alunoToken);
        var transporte = dashboard.aluno().transporte();
        assertThat(transporte.viagensHoje()).extracting(ViagemResumo::id)
                .containsExactly(antiga.getId(), futura.getId(), semData.getId());
        assertThat(transporte.viagensHoje()).extracting(ViagemResumo::dataOcorrencia).containsOnly(HOJE);
        assertThat(transporte.agendaSemanal().getLast().id()).isEqualTo(terca.getId());
        assertThat(transporte.programacaoSemGrade()).extracting(ViagemResumo::id)
                .containsExactly(semGrade.getId(), gradeSemDia.getId());
        assertThat(transporte.programacaoSemGrade()).allSatisfy(v -> assertThat(v.dataOcorrencia()).isNull());
        assertThat(antiga.getData()).isEqualTo(LocalDate.of(2006, 8, 12));
        assertThat(futura.getData()).isEqualTo(HOJE.plusYears(20));
        assertThat(semGrade.getData()).isEqualTo(HOJE);
    }

    @Test
    void usaSaoPauloMesmoQuandoDiaUtcJaMudouEOrdenaViradaDaSemana() {
        when(dashboardClock.instant()).thenReturn(Instant.parse("2026-10-12T01:30:00Z")); // Domingo em São Paulo.
        Rota rota = rota("Semana", faculdade);
        viagem(rota, DiaSemana.SEXTA, motorista, null, faculdade);
        viagem(rota, DiaSemana.SEGUNDA, motorista, null, faculdade);
        autenticar(alunoToken);
        var resposta = dashboard.aluno();
        assertThat(resposta.dataReferencia()).isEqualTo(LocalDate.of(2026, 10, 11));
        assertThat(resposta.geradoEm().getOffset()).isEqualTo(ZoneOffset.ofHours(-3));
        assertThat(resposta.transporte().viagensHoje()).isEmpty();
        assertThat(resposta.transporte().agendaSemanal()).extracting(ViagemResumo::dataOcorrencia)
                .containsExactly(HOJE, LocalDate.of(2026, 10, 16));
        when(dashboardClock.instant()).thenReturn(Instant.parse("2026-11-01T01:30:00Z"));
        autenticar(adminToken);
        assertThat(dashboard.admin(null).mes()).isEqualTo("2026-10"); // Ainda outubro em São Paulo.
    }

    @Test
    void faltaDeFaculdadeOuTransporteRetornaAvisosEValoresVazios() throws Exception {
        aluno.setFaculdade(null);
        mvc.perform(get("/dashboard/aluno").header("Authorization", alunoToken)).andExpect(status().isOk())
                .andExpect(jsonPath("$.faculdade").isEmpty())
                .andExpect(jsonPath("$.transporte.agendaSemanal.length()").value(0))
                .andExpect(jsonPath("$.financeiro.cobrancaPrincipal").isEmpty())
                .andExpect(jsonPath("$.financeiro.emAberto.valor").value(0))
                .andExpect(jsonPath("$.avisos[0]").exists());
        mvc.perform(get("/dashboard/motorista").header("Authorization", motoristaToken)).andExpect(status().isOk())
                .andExpect(jsonPath("$.avisos[0]").exists());
    }

    @Test
    void viagensSemMotoristaOuOnibusIndisponivelContinuamVisiveis() {
        onibus.setStatusOnibus(StatusOnibus.QUEBRADO);
        Viagem viagem = viagem(rota("Problema", faculdade), DiaSemana.SEGUNDA, null, null, faculdade);
        autenticar(alunoToken);
        var resposta = dashboard.aluno();
        assertThat(resposta.transporte().viagensHoje()).singleElement().satisfies(v -> {
            assertThat(v.id()).isEqualTo(viagem.getId()); assertThat(v.onibusDisponivel()).isFalse();
            assertThat(v.motorista()).isNull(); assertThat(v.faculdades()).hasSize(1);
            assertThat(v.avisos()).hasSize(2);
        });
        assertThat(viagem.getOnibus()).isSameAs(onibus);
    }

    @Test
    void cobrancaPrincipalPriorizaVencidaMaisAntigaECalculaTotaisSemAlterarStatus() {
        ContaReceber antiga = conta(aluno, HOJE.minusDays(20), "100.10", StatusFinanceiro.PENDENTE);
        conta(aluno, HOJE.minusDays(3), "200.20", StatusFinanceiro.PENDENTE);
        conta(aluno, HOJE, "300.30", StatusFinanceiro.PENDENTE);
        conta(aluno, HOJE.plusDays(10), "400.40", StatusFinanceiro.ATRASADO);
        conta(aluno, HOJE.minusDays(30), "900.00", StatusFinanceiro.PAGO);
        conta(outroAluno, HOJE.minusDays(50), "9999.00", StatusFinanceiro.PENDENTE);
        autenticar(alunoToken);
        var financeiro = dashboard.aluno().financeiro();
        assertThat(financeiro.mensalidadeCadastro()).isEqualByComparingTo("250.00");
        assertThat(financeiro.cobrancaPrincipal().id()).isEqualTo(antiga.getId());
        assertThat(financeiro.cobrancaPrincipal().situacao()).isEqualTo(SituacaoCobranca.VENCIDA);
        assertThat(financeiro.cobrancaPrincipal().diasParaVencimento()).isEqualTo(-20L);
        assertThat(financeiro.emAberto().quantidade()).isEqualTo(4);
        assertThat(financeiro.emAberto().valor()).isEqualByComparingTo("1001.00");
        assertThat(financeiro.vencidas().quantidade()).isEqualTo(2);
        assertThat(financeiro.vencidas().valor()).isEqualByComparingTo("300.30");
        assertThat(antiga.getStatus()).isEqualTo(StatusFinanceiro.PENDENTE);
        assertThat(aluno.getStatusMatricula()).isEqualTo(StatusMatricula.PENDENTE);
    }

    @Test
    void vencimentoHojeFuturoEPagaNuncaAtrasada() {
        ContaReceber hoje = conta(aluno, HOJE, "100.00", StatusFinanceiro.PENDENTE);
        ContaReceber futuro = conta(aluno, HOJE.plusDays(7), "200.00", StatusFinanceiro.ATRASADO);
        autenticar(alunoToken);
        assertThat(dashboard.aluno().financeiro().cobrancaPrincipal().situacao()).isEqualTo(SituacaoCobranca.VENCE_HOJE);
        assertThat(dashboard.aluno().financeiro().cobrancaPrincipal().diasParaVencimento()).isZero();
        hoje.setStatus(StatusFinanceiro.PAGO);
        assertThat(dashboard.aluno().financeiro().cobrancaPrincipal().id()).isEqualTo(futuro.getId());
        assertThat(dashboard.aluno().financeiro().cobrancaPrincipal().situacao()).isEqualTo(SituacaoCobranca.A_VENCER);
        futuro.setStatus(StatusFinanceiro.PAGO);
        var principal = dashboard.aluno().financeiro().cobrancaPrincipal();
        assertThat(principal.id()).isEqualTo(futuro.getId());
        assertThat(principal.situacao()).isEqualTo(SituacaoCobranca.PAGO);
        assertThat(principal.diasParaVencimento()).isNull();
        when(dashboardClock.instant()).thenReturn(Instant.parse("2026-11-12T15:00:00Z"));
        assertThat(dashboard.aluno().financeiro().cobrancaPrincipal().situacao()).isEqualTo(SituacaoCobranca.PAGO);
        assertThat(dashboard.aluno().financeiro().vencidas().quantidade()).isZero();
    }

    @Test
    void pagamentoExistenteAtualizaDashboardSemGerarNovaCobranca() throws Exception {
        ContaReceber conta = conta(aluno, HOJE.minusDays(10), "250.00", StatusFinanceiro.PENDENTE);
        long quantidadeAntes = contas.count();
        mvc.perform(post("/recebimentos/realizar/{id}", conta.getId()).param("formaPagamento", "PIX")
                        .header("Authorization", alunoToken)).andExpect(status().isOk());
        mvc.perform(get("/dashboard/aluno").header("Authorization", alunoToken)).andExpect(status().isOk())
                .andExpect(jsonPath("$.financeiro.cobrancaPrincipal.situacao").value("PAGO"))
                .andExpect(jsonPath("$.financeiro.cobrancaPrincipal.diasParaVencimento").isEmpty())
                .andExpect(jsonPath("$.financeiro.emAberto.quantidade").value(0))
                .andExpect(jsonPath("$.aluno.statusMatricula").value("ATIV0"));
        assertThat(contas.count()).isEqualTo(quantidadeAntes);
    }

    @Test
    void financeiroAdminUsaMovimentosReaisLimitesMensaisESerieSemDuplicar() {
        ContaReceber a = conta(aluno, HOJE.minusDays(2), "100.00", StatusFinanceiro.PAGO);
        ContaReceber b = conta(aluno, HOJE.minusDays(1), "200.00", StatusFinanceiro.PAGO);
        receber(a, LocalDate.of(2026, 10, 1), "1.10");
        receber(b, LocalDate.of(2026, 10, 31), "2.20");
        receber(conta(outroAluno, HOJE.plusMonths(1), "5000.00", StatusFinanceiro.PAGO), LocalDate.of(2026, 11, 1), "5000.00");
        receber(conta(outroAluno, HOJE.plusMonths(2), "50.00", StatusFinanceiro.PAGO), LocalDate.of(2026, 5, 1), "50.00");
        receber(conta(outroAluno, HOJE.plusMonths(3), "999.00", StatusFinanceiro.PAGO), LocalDate.of(2026, 4, 30), "999.00");
        ContaPagar salario = pagarConta(HOJE, "99999.00", StatusFinanceiro.PAGO);
        pagar(salario, LocalDate.of(2026, 10, 1), "5.50");
        pagar(salario, LocalDate.of(2026, 10, 31), "6.25");
        pagar(salario, LocalDate.of(2026, 11, 1), "999.00");
        conta(aluno, HOJE.minusDays(10), "10.10", StatusFinanceiro.PENDENTE);
        conta(aluno, HOJE, "20.20", StatusFinanceiro.PENDENTE);
        conta(aluno, HOJE.plusDays(6), "30.30", StatusFinanceiro.PENDENTE);
        conta(aluno, HOJE.plusDays(7), "40.40", StatusFinanceiro.PENDENTE);
        autenticar(adminToken);
        var financeiro = dashboard.admin("2026-10").financeiro();
        assertThat(financeiro.recebidoNoMes()).isEqualByComparingTo("3.30");
        assertThat(financeiro.pagoNoMes()).isEqualByComparingTo("11.75");
        assertThat(financeiro.saldoMovimentacoes()).isEqualByComparingTo("-8.45");
        assertThat(financeiro.serieMensal()).hasSize(6);
        assertThat(financeiro.serieMensal().getFirst().mes()).isEqualTo("2026-05");
        assertThat(financeiro.serieMensal().getFirst().recebido()).isEqualByComparingTo("50.00");
        assertThat(financeiro.contasReceber().emAberto().valor()).isEqualByComparingTo("397.70");
        assertThat(financeiro.contasReceber().vencidas().quantidade()).isEqualTo(3);
        assertThat(financeiro.contasReceber().vencemEmSeteDias().quantidade()).isEqualTo(2);
        assertThat(financeiro.contasReceber().vencemEmSeteDias().valor()).isEqualByComparingTo("50.50");
    }

    @Test
    void operacaoAdminContaEstadosESemGradeEPriorizaCincoChamados() {
        aluno.setStatusMatricula(StatusMatricula.ATIV0); outroAluno.setStatusMatricula(StatusMatricula.INATIV0);
        em.persist(alunoSemConta(StatusMatricula.PENDENTE)); em.persist(alunoSemConta(null));
        for (StatusOnibus estado : List.of(StatusOnibus.MANUTENCAO, StatusOnibus.QUEBRADO, StatusOnibus.INATIVO)) {
            Onibus novo = new Onibus(); novo.setStatusOnibus(estado); em.persist(novo);
        }
        em.persist(new Onibus());
        Rota rota = rota("Operação", faculdade);
        viagem(rota, DiaSemana.SEGUNDA, motorista, HOJE.plusYears(50), faculdade);
        viagem(rota, DiaSemana.SEGUNDA, outroMotorista, null, outraFaculdade);
        viagem(rota, DiaSemana.TERCA, motorista, HOJE, faculdade);
        viagem(rota, null, motorista, HOJE, faculdade);
        chamado(NivelUrgencia.BAIXA, StatusChamado.ABERTO, HOJE.minusDays(50));
        ChamadoManutencao maisAntigo = chamado(NivelUrgencia.ALTA, StatusChamado.EM_ANDAMENTO, HOJE.minusDays(10));
        for (int i = 0; i < 5; i++) chamado(NivelUrgencia.ALTA, StatusChamado.ABERTO, HOJE.minusDays(i));
        chamado(NivelUrgencia.ALTA, StatusChamado.RESOLVIDO, HOJE.minusDays(100));
        for (int i = 0; i < 7; i++) pagarConta(HOJE.minusDays(i), "100.00", StatusFinanceiro.PENDENTE);
        autenticar(adminToken);
        var resposta = dashboard.admin(null);
        assertThat(resposta.mes()).isEqualTo("2026-10");
        var op = resposta.operacao();
        assertThat(op.alunos()).isEqualTo(new AlunosIndicadores(4, 1, 1, 1, 1));
        assertThat(op.onibus()).isEqualTo(new OnibusIndicadores(5, 1, 1, 1, 1, 1));
        assertThat(op.viagensHoje()).isEqualTo(2); assertThat(op.viagensSemProgramacao()).isEqualTo(1);
        assertThat(op.chamados()).isEqualTo(new ChamadosIndicadores(6, 1, 6));
        assertThat(op.chamadosPendentes()).hasSize(5);
        assertThat(op.chamadosPendentes().getFirst().id()).isEqualTo(maisAntigo.getId());
        assertThat(op.chamadosPendentes()).allSatisfy(c -> assertThat(c.urgencia()).isEqualTo(NivelUrgencia.ALTA));
        assertThat(resposta.financeiro().pendenciasPagar()).hasSize(5);
        assertThat(resposta.financeiro().pendenciasPagar().getFirst().dataVencimento()).isEqualTo(HOJE.minusDays(6));
    }

    @Test
    void adminSemMovimentosTemTotaisZeroESerieCompleta() {
        autenticar(adminToken);
        var financeiro = dashboard.admin(null).financeiro();
        assertThat(financeiro.recebidoNoMes()).isZero(); assertThat(financeiro.pagoNoMes()).isZero();
        assertThat(financeiro.contasReceber().emAberto().quantidade()).isZero();
        assertThat(financeiro.contasPagar().vencidas().valor()).isZero();
        assertThat(financeiro.serieMensal()).hasSize(6).allSatisfy(m -> assertThat(m.recebido()).isZero());
        assertThat(financeiro.pendenciasReceber()).isEmpty();
    }

    @Test
    void matrizDePermissoesEValidacaoDoMes() throws Exception {
        List<String> caminhos = List.of("/dashboard/aluno", "/dashboard/motorista", "/dashboard/admin");
        for (String caminho : caminhos) mvc.perform(get(caminho)).andExpect(status().isUnauthorized());
        for (String token : List.of(alunoToken, motoristaToken, adminToken)) {
            String permitido = token.equals(alunoToken) ? caminhos.get(0) : token.equals(motoristaToken) ? caminhos.get(1) : caminhos.get(2);
            for (String caminho : caminhos) mvc.perform(get(caminho).header("Authorization", token))
                    .andExpect(status().is(caminho.equals(permitido) ? 200 : 403));
        }
        for (String mes : List.of("", "2026-1", "2026-13", "10/2026", "2026-10-01", "0000-10", "2026-10 OR 1=1")) {
            mvc.perform(get("/dashboard/admin").param("mes", mes).header("Authorization", adminToken))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        }
        SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> dashboard.admin(null)).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        autenticar(alunoToken);
        assertThatThrownBy(() -> dashboard.admin(null)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> dashboard.motorista()).isInstanceOf(AccessDeniedException.class);
        autenticar(adminToken);
        assertThatThrownBy(() -> dashboard.aluno()).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void registrosLegadosSemVencimentoOuDadosDeTransporteNaoInventamInformacoes() {
        ContaReceber conta = conta(aluno, null, "123.45", null);
        Viagem viagem = viagem(null, DiaSemana.QUARTA, null, HOJE, faculdade);
        viagem.setOnibus(null);
        autenticar(alunoToken);
        var resposta = dashboard.aluno();
        assertThat(resposta.financeiro().cobrancaPrincipal().situacao()).isEqualTo(SituacaoCobranca.SEM_VENCIMENTO);
        assertThat(resposta.financeiro().cobrancaPrincipal().diasParaVencimento()).isNull();
        assertThat(resposta.financeiro().emAberto().quantidade()).isEqualTo(1);
        assertThat(resposta.financeiro().vencidas().quantidade()).isZero();
        assertThat(resposta.transporte().agendaSemanal()).singleElement().satisfies(v -> {
            assertThat(v.rota()).isNull(); assertThat(v.onibus()).isNull();
            assertThat(v.onibusDisponivel()).isFalse(); assertThat(v.avisos()).hasSize(3);
        });
        autenticar(adminToken);
        var financeiro = dashboard.admin(null).financeiro();
        assertThat(financeiro.contasReceber().emAberto().valor()).isEqualByComparingTo("123.45");
        assertThat(financeiro.contasReceber().vencidas().valor()).isZero();
        assertThat(financeiro.pendenciasReceber().getFirst().id()).isEqualTo(conta.getId());
    }

    @Test
    void consultasNaoAlteramDadosNemCriamContas() {
        ContaReceber conta = conta(aluno, HOJE.minusDays(1), "250.00", StatusFinanceiro.PENDENTE);
        Viagem viagem = viagem(rota("Sem alterações", faculdade), DiaSemana.SEGUNDA, motorista, LocalDate.of(2006, 1, 1), faculdade);
        em.flush();
        long quantidade = contas.count();
        autenticar(alunoToken); dashboard.aluno(); dashboard.aluno();
        autenticar(motoristaToken); dashboard.motorista();
        autenticar(adminToken); dashboard.admin(null);
        em.flush(); em.clear();
        assertThat(contas.count()).isEqualTo(quantidade);
        assertThat(em.find(ContaReceber.class, conta.getId()).getStatus()).isEqualTo(StatusFinanceiro.PENDENTE);
        assertThat(em.find(Aluno.class, aluno.getId()).getStatusMatricula()).isEqualTo(StatusMatricula.PENDENTE);
        assertThat(em.find(Viagem.class, viagem.getId()).getData()).isEqualTo(LocalDate.of(2006, 1, 1));
    }

    private Faculdade faculdade(String nome) {
        Faculdade f = new Faculdade(); f.setNome(nome); f.setEndereco("Endereço " + nome); em.persist(f); return f;
    }
    private Aluno alunoSemConta(StatusMatricula status) {
        Aluno a = new Aluno(); a.setNome("Aluno"); a.setTipoPessoa(TipoPessoa.ALUNO); a.setStatusMatricula(status); return a;
    }
    private Aluno aluno(String nome, Faculdade f, String email) {
        Aluno a = alunoSemConta(StatusMatricula.PENDENTE); a.setNome(nome); a.setFaculdade(f);
        a.setMatricula(nome); a.setMensalidade(new BigDecimal("250.00")); a.setCpfCnpj("cpf-privado"); em.persist(a);
        usuarios.criarUsuario(new CriarUsuarioRequest(a.getId(), email, "SenhaTeste123")); return a;
    }
    private Motorista motorista(String nome, String email) {
        Motorista m = new Motorista(); m.setNome(nome); m.setTipoPessoa(TipoPessoa.MOTORISTA);
        m.setCnh("cnh-privada"); m.setSalario(new BigDecimal("3000.00")); em.persist(m);
        usuarios.criarUsuario(new CriarUsuarioRequest(m.getId(), email, "SenhaTeste123")); return m;
    }
    private Rota rota(String nome, Faculdade f) {
        Rota r = new Rota(); r.setNome(nome); r.setDescricao("Descrição " + nome); r.setFaculdade(f);
        r.setPontosEmbarque(new ArrayList<>()); em.persist(r); return r;
    }
    private PontoEmbarque ponto(String descricao, Integer ordem, Rota rota) {
        PontoEmbarque p = new PontoEmbarque(); p.setDescricao(descricao); p.setOrdemParada(ordem); p.setRota(rota); em.persist(p); return p;
    }
    private Viagem viagem(Rota rota, DiaSemana dia, Motorista motorista, LocalDate data, Faculdade... destinos) {
        Viagem v = new Viagem(); v.setRota(rota); v.setMotorista(motorista); v.setOnibus(onibus); v.setData(data);
        v.setFaculdades(new ArrayList<>(List.of(destinos)));
        if (dia != null) { GradeDiaria g = new GradeDiaria(); g.setDiaSemana(dia); g.setDescricao("Grade " + dia); em.persist(g); v.setGradeDiaria(g); }
        em.persist(v); return v;
    }
    private ContaReceber conta(Aluno aluno, LocalDate vencimento, String valor, StatusFinanceiro status) {
        ContaReceber c = new ContaReceber(); c.setAluno(aluno); c.setDescricao("Cobrança " + aluno.getNome());
        c.setDataVencimento(vencimento); c.setValor(new BigDecimal(valor)); c.setStatus(status); em.persist(c); return c;
    }
    private ContaPagar pagarConta(LocalDate vencimento, String valor, StatusFinanceiro status) {
        ContaPagar c = new ContaPagar(); c.setMotorista(motorista); c.setDescricao("Salário"); c.setValor(new BigDecimal(valor));
        c.setDataVencimento(vencimento); c.setStatus(status); em.persist(c); return c;
    }
    private void receber(ContaReceber conta, LocalDate data, String valor) {
        Recebimento r = new Recebimento(); r.setContaReceber(conta); r.setDataRecebimento(data); r.setValor(new BigDecimal(valor));
        r.setFormaPagamento(FormaPagamento.PIX); em.persist(r);
    }
    private void pagar(ContaPagar conta, LocalDate data, String valor) {
        Pagamento p = new Pagamento(); p.setContaPagar(conta); p.setDataPagamento(data); p.setValor(new BigDecimal(valor));
        p.setFormaPagamento(FormaPagamento.PIX); em.persist(p);
    }
    private ChamadoManutencao chamado(NivelUrgencia prioridade, StatusChamado status, LocalDate abertura) {
        ChamadoManutencao c = new ChamadoManutencao(); c.setDescricao("Manutenção"); c.setOnibus(onibus); c.setSolicitante(aluno);
        c.setDataAbertura(abertura); c.setUrgencia(prioridade); c.setStatus(status); em.persist(c); return c;
    }
    private String token(String email) { return "Bearer " + auth.login(new LoginRequest(email, "SenhaTeste123")).accessToken(); }
    private void autenticar(String token) { SecurityContextHolder.getContext().setAuthentication(converter.convert(decoder.decode(token.substring(7)))); }
}
