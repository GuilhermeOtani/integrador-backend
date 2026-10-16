package com.example.integrador;

import com.example.integrador.DTO.*;
import com.example.integrador.Enum.*;
import com.example.integrador.Exception.*;
import com.example.integrador.Model.*;
import com.example.integrador.Repository.*;
import com.example.integrador.Service.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.test.context.support.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(roles = "ADMIN")
class RelatoriosIntegrationTests {
    @Autowired EntityManager em;
    @Autowired MockMvc mvc;
    @Autowired RelatoriosService relatorios;
    @Autowired RelatorioPdfService pdf;
    @Autowired PagamentoService pagamentos;
    @Autowired ContaPagarService contas;
    @Autowired SaldoBaseCaixaService caixa;
    @Autowired PagamentoRepository pagamentosRepository;
    @Autowired DashboardConsultasRepository dashboard;
    @MockitoBean Clock dashboardClock;
    Motorista motorista;
    Aluno aluno;
    @BeforeEach void preparar() {
        when(dashboardClock.instant()).thenReturn(Instant.parse("2026-10-16T15:00:00Z"));
        when(dashboardClock.getZone()).thenReturn(ZoneId.of("America/Sao_Paulo"));
        motorista = new Motorista(); motorista.setNome("José da Conceição"); motorista.setTipoPessoa(TipoPessoa.MOTORISTA);
        motorista.setSalario(new BigDecimal("3000")); em.persist(motorista);
        aluno = new Aluno(); aluno.setNome("João Álvares"); aluno.setMatricula("2026001"); aluno.setTelefone("(18) 99999-0000");
        aluno.setTipoPessoa(TipoPessoa.ALUNO); em.persist(aluno);
    }
    @Test void parcialQuitacaoRecibosSaldoEDashboard() {
        var c = despesa("1000", LocalDate.of(2026,10,10));
        var primeiro = pagamentos.realizarPagamento(c.getId(), FormaPagamento.PIX, new BigDecimal("600"));
        assertThat(c.getStatus()).isEqualTo(StatusFinanceiro.PENDENTE);
        var resposta = contas.buscarResposta(c.getId());
        assertThat(resposta.valorPago()).isEqualByComparingTo("600");
        assertThat(resposta.saldoRestante()).isEqualByComparingTo("400"); assertThat(resposta.pagamentoParcial()).isTrue();
        assertThat((BigDecimal) dashboard.resumoContas(DashboardConsultasRepository.TipoConta.PAGAR, LocalDate.of(2026,10,16))[1]).isEqualByComparingTo("400");
        var ultimo = pagamentos.realizarPagamento(c.getId(), FormaPagamento.DINHEIRO, null);
        assertThat(ultimo.getValor()).isEqualByComparingTo("400"); assertThat(c.getStatus()).isEqualTo(StatusFinanceiro.PAGO);
        assertThat(pagamentos.listarPorConta(c.getId())).hasSize(2);
        assertThat(pagamentos.buscarReciboDTOPorConta(c.getId()).getId()).isEqualTo(ultimo.getId()).isNotEqualTo(primeiro.getId());
        assertThatThrownBy(() -> pagamentos.realizarPagamento(c.getId(), FormaPagamento.PIX, null)).isInstanceOf(ConflitoException.class);
    }
    @Test void idempotenciaValidaConteudoERepeticaoDepoisDeQuitar() {
        var c = despesa("100", LocalDate.of(2026,10,10)); String chave = UUID.randomUUID().toString();
        var primeiro = pagamentos.realizarPagamento(c.getId(), FormaPagamento.PIX, null, chave);
        assertThat(pagamentos.realizarPagamento(c.getId(), FormaPagamento.PIX, null, chave).getId()).isEqualTo(primeiro.getId());
        assertThat(pagamentos.listarPorConta(c.getId())).hasSize(1);
        assertThatThrownBy(() -> pagamentos.realizarPagamento(c.getId(), FormaPagamento.DINHEIRO, null, chave)).isInstanceOf(ConflitoException.class);
        assertThatThrownBy(() -> pagamentos.realizarPagamento(c.getId(), FormaPagamento.PIX, null, "invalida")).isInstanceOf(RegraNegocioException.class);
    }
    @Test void rejeitaValoresInvalidosEProtegeHistorico() {
        var c = despesa("100", LocalDate.of(2026,10,10));
        for (String valor : List.of("0", "-1", "100.01", "1.001"))
            assertThatThrownBy(() -> pagamentos.realizarPagamento(c.getId(), FormaPagamento.PIX, new BigDecimal(valor))).isInstanceOf(RegraNegocioException.class);
        pagamentos.realizarPagamento(c.getId(), FormaPagamento.PIX, new BigDecimal("30"));
        assertThatThrownBy(() -> contas.deletarContaPagarPorId(c.getId())).isInstanceOf(ConflitoException.class);
        ContaPagar entrada = new ContaPagar(); entrada.setValor(new BigDecimal("80")); entrada.setMotorista(motorista);
        assertThatThrownBy(() -> contas.atualizarContaPagar(c.getId(), entrada)).isInstanceOf(ConflitoException.class);
    }
    @Test void competenciaIndependeDoVencimentoEPagamento() {
        var c = despesa("1000", LocalDate.of(2026,11,5)); c.setCompetencia(LocalDate.of(2026,9,1));
        pagamentos.realizarPagamento(c.getId(), FormaPagamento.PIX, new BigDecimal("600"));
        var r = relatorios.acerto("2026-09", motorista.getId());
        assertThat(r.totalPrevisto()).isEqualByComparingTo("1000"); assertThat(r.totalPago()).isEqualByComparingTo("600");
        assertThat(r.saldoAPagar()).isEqualByComparingTo("400");
        assertThat(r.motoristas().getFirst().lancamentos().getFirst().pagamentoParcial()).isTrue();
        assertThat(relatorios.acerto("2026-11", motorista.getId()).motoristas()).isEmpty();
    }
    @Test void inadimplenciaFiltraVencidasIncluiArquivadosESuperaCinco() {
        aluno.setArquivado(true);
        for (int i=1; i<=7; i++) cobrar(LocalDate.of(2026,10,i), "20", StatusFinanceiro.PENDENTE);
        cobrar(LocalDate.of(2026,10,16), "999", StatusFinanceiro.PENDENTE);
        cobrar(LocalDate.of(2026,10,17), "999", StatusFinanceiro.PENDENTE);
        cobrar(LocalDate.of(2026,9,1), "999", StatusFinanceiro.PAGO);
        var r = relatorios.inadimplencia(null, null, null);
        assertThat(r.quantidadeCobrancas()).isEqualTo(7); assertThat(r.totalGeral()).isEqualByComparingTo("140");
        assertThat(r.alunos().getFirst().arquivado()).isTrue();
        assertThat(relatorios.inadimplencia(aluno.getId(), LocalDate.of(2026,10,2), LocalDate.of(2026,10,3)).totalGeral()).isEqualByComparingTo("40");
    }
    @Test void fluxoApuraBaseHistoricoLimitesESaldoNegativo() {
        caixa.salvar(new SaldoBaseDTO(LocalDate.of(2026,10,1), new BigDecimal("100"), null));
        receber(cobrar(LocalDate.of(2026,9,1), "50", StatusFinanceiro.PAGO), LocalDate.of(2026,10,1), "50");
        receber(cobrar(LocalDate.of(2026,9,2), "20", StatusFinanceiro.PAGO), LocalDate.of(2026,10,10), "20");
        receber(cobrar(LocalDate.of(2026,9,3), "30", StatusFinanceiro.PAGO), LocalDate.of(2026,10,16), "30");
        receber(cobrar(LocalDate.of(2026,9,4), "999", StatusFinanceiro.PAGO), LocalDate.of(2026,10,17), "999");
        pagamentos.realizarPagamento(despesa("1000", LocalDate.of(2026,10,16)).getId(), FormaPagamento.PIX, new BigDecimal("500"));
        var r = relatorios.fluxoCaixa(LocalDate.of(2026,10,10), LocalDate.of(2026,10,16));
        assertThat(r.saldoInicial()).isEqualByComparingTo("150"); assertThat(r.totalEntradas()).isEqualByComparingTo("50");
        assertThat(r.totalSaidas()).isEqualByComparingTo("500"); assertThat(r.saldoFinal()).isEqualByComparingTo("-300");
        assertThat(r.movimentos()).hasSize(3); assertThat(r.movimentos().getLast().saldoAcumulado()).isEqualByComparingTo(r.saldoFinal());
    }
    @Test void saldoBaseExigeVersaoEPeriodoValido() {
        assertThatThrownBy(() -> relatorios.fluxoCaixa(LocalDate.of(2026,10,1), LocalDate.of(2026,10,2))).isInstanceOf(RecursoNaoEncontradoException.class);
        var base = caixa.salvar(new SaldoBaseDTO(LocalDate.of(2026,10,1), BigDecimal.ZERO, null));
        var nova = caixa.salvar(new SaldoBaseDTO(base.dataReferencia(), BigDecimal.TEN, base.versao()));
        assertThat(nova.versao()).isGreaterThan(base.versao());
        assertThatThrownBy(() -> caixa.salvar(base)).isInstanceOf(ConflitoException.class);
        assertThatThrownBy(() -> relatorios.fluxoCaixa(LocalDate.of(2026,9,30), LocalDate.of(2026,10,2))).isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> relatorios.inadimplencia(null, LocalDate.of(2026,10,2), LocalDate.of(2026,10,1))).isInstanceOf(RegraNegocioException.class);
    }
    @Test void recorrenciaMensalPreservaDia31EIgnoraDespesas() {
        when(dashboardClock.instant()).thenReturn(Instant.parse("2027-01-31T15:00:00Z"));
        var c = despesa("3000", LocalDate.of(2027,1,31)); c.setClassificacao(ClassificacaoConta.SALARIO);
        c.setDiaVencimentoSalario(31); c.setCompetencia(LocalDate.of(2027,1,1)); em.flush();
        despesa("50", LocalDate.of(2030,1,1));
        contas.gerarSalarioRecorrente(motorista.getId()); contas.gerarSalarioRecorrente(motorista.getId());
        var fevereiro = em.createQuery("select c from ContaPagar c where c.classificacao = :tipo and c.competencia = :mes", ContaPagar.class)
                .setParameter("tipo", ClassificacaoConta.SALARIO).setParameter("mes",LocalDate.of(2027,2,1)).getSingleResult();
        assertThat(fevereiro.getDataVencimento()).isEqualTo(LocalDate.of(2027,2,28));
        when(dashboardClock.instant()).thenReturn(Instant.parse("2027-02-28T15:00:00Z")); contas.gerarSalarioRecorrente(motorista.getId());
        var marco = em.createQuery("select c from ContaPagar c where c.classificacao = :tipo and c.competencia = :mes", ContaPagar.class)
                .setParameter("tipo", ClassificacaoConta.SALARIO).setParameter("mes",LocalDate.of(2027,3,1)).getSingleResult();
        assertThat(marco.getDataVencimento()).isEqualTo(LocalDate.of(2027,3,31));
    }
    @Test void salarioRequerMotoristaECompetenciaSemBloquearDespesaGenerica() {
        var c = new ContaPagar(); c.setMotorista(aluno); c.setClassificacao(ClassificacaoConta.SALARIO);
        c.setCompetencia(LocalDate.of(2026,10,1)); c.setDataVencimento(LocalDate.of(2026,10,30));
        assertThatThrownBy(() -> contas.salvarContaPagar(c)).isInstanceOf(RegraNegocioException.class);
        c.setClassificacao(ClassificacaoConta.DESPESA); assertThat(contas.salvarContaPagar(c).getMotorista()).isEqualTo(aluno);
        assertThat(relatorios.acerto("2026-10", null).motoristas()).isEmpty();
    }
    @Test void recorrenciaCobreAnoBissextoEViradaDeAno() {
        for (LocalDate origem : List.of(LocalDate.of(2028,1,31), LocalDate.of(2026,12,31))) {
            when(dashboardClock.instant()).thenReturn(origem.atTime(12,0).atZone(ZoneId.of("America/Sao_Paulo")).toInstant());
            Motorista m = new Motorista(); m.setNome("Motorista " + origem); m.setSalario(new BigDecimal("1000")); em.persist(m);
            ContaPagar c = new ContaPagar(); c.setMotorista(m); c.setValor(m.getSalario()); c.setClassificacao(ClassificacaoConta.SALARIO);
            c.setCompetencia(origem.withDayOfMonth(1)); c.setDataVencimento(origem); contas.salvarContaPagar(c);
            contas.gerarSalarioRecorrente(m.getId());
            var proxima = em.createQuery("select c from ContaPagar c where c.motorista.id=:id and c.competencia=:mes",ContaPagar.class)
                    .setParameter("id",m.getId()).setParameter("mes",origem.withDayOfMonth(1).plusMonths(1)).getSingleResult();
            assertThat(proxima.getDataVencimento()).isEqualTo(origem.plusMonths(1));
        }
    }
    @Test void novoSalarioDuplicadoEhRejeitado() {
        for (int i=0;i<2;i++) {
            ContaPagar c = new ContaPagar(); c.setMotorista(motorista); c.setValor(new BigDecimal("3000"));
            c.setClassificacao(ClassificacaoConta.SALARIO); c.setCompetencia(LocalDate.of(2026,10,1)); c.setDataVencimento(LocalDate.of(2026,11,5));
            if (i==0) contas.salvarContaPagar(c);
            else assertThatThrownBy(() -> contas.salvarContaPagar(c)).isInstanceOf(ConflitoException.class);
        }
    }
    @Test void downloadExpoeNomeEPreflightPermiteIdempotencia() throws Exception {
        mvc.perform(options("/pagamentos/realizar/1").header("Origin","http://localhost:4200")
                .header("Access-Control-Request-Method","POST").header("Access-Control-Request-Headers","Authorization,Idempotency-Key"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Headers",org.hamcrest.Matchers.containsString("Idempotency-Key")));
        mvc.perform(get("/relatorios/inadimplencia/pdf").header("Origin","http://localhost:4200"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Expose-Headers",org.hamcrest.Matchers.containsString("Content-Disposition")));
    }
    @Test void divergenciasLegadasSaoExibidasSemInventarPagamentos() {
        var c = despesa("100", LocalDate.of(2026,10,10)); c.setStatus(StatusFinanceiro.PAGO);
        var posicao = contas.buscarResposta(c.getId());
        assertThat(posicao.saldoRestante()).isZero(); assertThat(posicao.valorPago()).isZero();
        assertThat(posicao.avisos()).contains("QUITACAO_SEM_COMPROVANTE");
        var semCompetencia = despesa("50",null); semCompetencia.setCompetencia(null);
        assertThat(relatorios.acerto("2026-10",null).avisos()).anyMatch(a -> a.contains(semCompetencia.getId().toString()));
        caixa.salvar(new SaldoBaseDTO(LocalDate.of(2026,10,1),BigDecimal.ZERO,null));
        assertThat(relatorios.fluxoCaixa(LocalDate.of(2026,10,1),LocalDate.of(2026,10,31)).totalSaidas()).isZero();
    }
    @Test void endpointsValidamEntradasERetornamPdf() throws Exception {
        mvc.perform(get("/relatorios/acerto-motoristas").param("mes","2026-13")).andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        mvc.perform(get("/relatorios/fluxo-caixa")).andExpect(status().isBadRequest());
        mvc.perform(get("/relatorios/inadimplencia").param("vencimentoInicio","2026-99-01")).andExpect(status().isBadRequest());
        mvc.perform(get("/relatorios/inadimplencia").param("alunoId","9999999")).andExpect(status().isNotFound());
        mvc.perform(get("/relatorios/acerto-motoristas/pdf").param("mes","2026-10"))
                .andExpect(status().isOk()).andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Content-Disposition","attachment; filename=\"acerto-motoristas.pdf\""));
    }
    @Test @WithMockUser(roles="ALUNO") void alunoNaoAcessaRelatoriosNemServicos() throws Exception {
        mvc.perform(get("/relatorios/inadimplencia")).andExpect(status().isForbidden());
        mvc.perform(get("/financeiro/saldo-base")).andExpect(status().isForbidden());
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("aluno", "", List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ALUNO"))));
        assertThatThrownBy(() -> relatorios.acerto("2026-10",null)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThatThrownBy(() -> pdf.inadimplencia(null,null,null)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }
    @Test @WithMockUser(roles="MOTORISTA") void motoristaNaoAcessaFinanceiro() throws Exception {
        mvc.perform(get("/relatorios/acerto-motoristas").param("mes","2026-10")).andExpect(status().isForbidden());
        mvc.perform(get("/pagamentos/conta/1")).andExpect(status().isForbidden());
    }
    @Test @WithAnonymousUser void exigeAutenticacao() throws Exception {
        mvc.perform(get("/relatorios/inadimplencia/pdf")).andExpect(status().isUnauthorized());
    }
    @Test void geraExemplosComAcentosDescricoesLongasVariasPaginasEVazios() throws Exception {
        Path pasta = Path.of("target/relatorios-exemplos"); Files.createDirectories(pasta);
        caixa.salvar(new SaldoBaseDTO(LocalDate.of(2026,1,1),new BigDecimal("2500"),null));
        Files.write(pasta.resolve("inadimplencia-vazio.pdf"),pdf.inadimplencia(null,null,null));
        Files.write(pasta.resolve("acerto-vazio.pdf"),pdf.acerto("2026-10",null));
        Files.write(pasta.resolve("fluxo-vazio.pdf"),pdf.fluxoCaixa(LocalDate.of(2026,10,1),LocalDate.of(2026,10,31)));
        for (int i=1;i<=45;i++) {
            var cobranca = cobrar(LocalDate.of(2026,7,1).plusDays(i),"125.50",StatusFinanceiro.PENDENTE);
            cobranca.setDescricao("Mensalidade de transporte universitário - descrição extensa para conferir quebra de linha, legibilidade e preservação dos acentos brasileiros.");
            var conta = despesa("100.00",LocalDate.of(2026,10,1).plusDays(i%28));
            conta.setDescricao("Reembolso de combustível e despesas de viagem universitária com descrição longa para testar o documento em várias páginas.");
            pagamentos.realizarPagamento(conta.getId(),FormaPagamento.PIX,new BigDecimal("25.50"));
        }
        Files.write(pasta.resolve("inadimplencia.pdf"),pdf.inadimplencia(null,null,null));
        Files.write(pasta.resolve("acerto-motoristas.pdf"),pdf.acerto("2026-10",null));
        Files.write(pasta.resolve("fluxo-caixa.pdf"),pdf.fluxoCaixa(LocalDate.of(2026,10,1),LocalDate.of(2026,10,31)));
        for (String nome : List.of("inadimplencia","acerto-motoristas","fluxo-caixa")) {
            byte[] bytes = Files.readAllBytes(pasta.resolve(nome+".pdf"));
            assertThat(new String(bytes,0,4,java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF");
            assertThat(bytes.length).isGreaterThan(10000);
        }
    }
    ContaPagar despesa(String valor, LocalDate vencimento) {
        var c = new ContaPagar(); c.setMotorista(motorista); c.setValor(new BigDecimal(valor)); c.setDescricao("Despesa de viagem");
        c.setDataVencimento(vencimento); return contas.salvarContaPagar(c);
    }
    ContaReceber cobrar(LocalDate vencimento, String valor, StatusFinanceiro status) {
        var c = new ContaReceber(); c.setAluno(aluno); c.setDataVencimento(vencimento); c.setValor(new BigDecimal(valor)); c.setStatus(status);
        c.setDescricao("Mensalidade"); em.persist(c); return c;
    }
    void receber(ContaReceber conta, LocalDate data, String valor) {
        var r = new Recebimento(); r.setContaReceber(conta); r.setDataRecebimento(data); r.setValor(new BigDecimal(valor)); r.setFormaPagamento(FormaPagamento.PIX); em.persist(r);
    }
}
