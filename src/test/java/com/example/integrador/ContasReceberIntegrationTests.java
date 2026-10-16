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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(roles="ALUNO")
class ContasReceberIntegrationTests {
    @Autowired EntityManager em;
    @Autowired MockMvc mvc;
    @Autowired ContaReceberService contas;
    @Autowired RecebimentoService recebimentos;
    @Autowired RecebimentoRepository repository;
    @Autowired RelatoriosService relatorios;
    @Autowired RelatorioPdfService pdf;
    @Autowired DashboardService dashboard;
    @Autowired DashboardConsultasRepository consultasDashboard;
    @Autowired UsuarioService usuarios;
    @Autowired AuthService auth;
    @MockitoBean Clock dashboardClock;
    Aluno aluno;
    @BeforeEach void preparar() {
        when(dashboardClock.instant()).thenReturn(Instant.parse("2026-10-16T15:00:00Z"));
        when(dashboardClock.getZone()).thenReturn(ZoneId.of("America/Sao_Paulo"));
        aluno=novoAluno("João da Conceição");
    }
    Aluno novoAluno(String nome) {
        Aluno a=new Aluno(); a.setNome(nome); a.setMensalidade(new BigDecimal("300"));
        a.setTipoPessoa(TipoPessoa.ALUNO); a.setStatusMatricula(StatusMatricula.PENDENTE); em.persist(a); return a;
    }
    ContaReceber cobrar(LocalDate vencimento) { return contas.gerarProximaConta(aluno,vencimento); }
    static void papel(String papel) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("teste","",List.of(new SimpleGrantedAuthority("ROLE_"+papel))));
    }
    String token(Aluno a) {
        String email="aluno"+a.getId()+"@parcial.test";
        usuarios.criarUsuario(new CriarUsuarioRequest(a.getId(),email,"SenhaTeste123"));
        return "Bearer "+auth.login(new LoginRequest(email,"SenhaTeste123")).accessToken();
    }
    @Test void parcialMantemMatriculaPendenteEApenasQuitacaoAtiva() {
        var c=cobrar(LocalDate.of(2026,10,10));
        var primeiro=recebimentos.realizarPagamento(c.getId(),FormaPagamento.PIX,aluno.getId(),new BigDecimal("100"),null);
        assertThat(c.getStatus()).isEqualTo(StatusFinanceiro.PENDENTE);
        assertThat(aluno.getStatusMatricula()).isEqualTo(StatusMatricula.PENDENTE);
        var pos=contas.listarMinhasContas(aluno.getId()).getFirst();
        assertThat(pos.valorRecebido()).isEqualByComparingTo("100"); assertThat(pos.saldoRestante()).isEqualByComparingTo("200");
        assertThat(pos.recebimentoParcial()).isTrue(); assertThat(pos.competencia()).isEqualTo("2026-10");
        var ultimo=recebimentos.realizarPagamento(c.getId(),FormaPagamento.DINHEIRO,aluno.getId());
        assertThat(ultimo.valor()).isEqualByComparingTo("200"); assertThat(c.getStatus()).isEqualTo(StatusFinanceiro.PAGO);
        assertThat(aluno.getStatusMatricula()).isEqualTo(StatusMatricula.ATIV0);
        assertThat(recebimentos.listarPorConta(c.getId(),aluno.getId())).extracting(RecebimentoResponse::id).containsExactly(ultimo.id(),primeiro.id());
        assertThat(recebimentos.buscarRecibo(c.getId(),aluno.getId()).id()).isEqualTo(ultimo.id());
    }
    @Test void repeticaoIdempotenteNaoDuplicaEValidaConteudo() {
        var c=cobrar(LocalDate.of(2026,10,10)); String chave=UUID.randomUUID().toString();
        var primeiro=recebimentos.realizarPagamento(c.getId(),FormaPagamento.PIX,aluno.getId(),new BigDecimal("100"),chave);
        assertThat(recebimentos.realizarPagamento(c.getId(),FormaPagamento.PIX,aluno.getId(),new BigDecimal("100.00"),chave).id()).isEqualTo(primeiro.id());
        assertThat(recebimentos.listarPorConta(c.getId(),aluno.getId())).hasSize(1);
        assertThatThrownBy(()->recebimentos.realizarPagamento(c.getId(),FormaPagamento.DINHEIRO,aluno.getId(),new BigDecimal("100"),chave)).isInstanceOf(ConflitoException.class);
    }
    @Test void replayDepoisDaQuitacaoRetornaOriginal() {
        var c=cobrar(LocalDate.of(2026,10,10)); String chave=UUID.randomUUID().toString();
        var r=recebimentos.realizarPagamento(c.getId(),FormaPagamento.PIX,aluno.getId(),null,chave);
        assertThat(recebimentos.realizarPagamento(c.getId(),FormaPagamento.PIX,aluno.getId(),null,chave).id()).isEqualTo(r.id());
        assertThat(repository.totalRecebido(c.getId())).isEqualByComparingTo("300");
    }
    @Test void rejeitaValoresInvalidosSaldoExcedidoEChaveInvalida() {
        var c=cobrar(LocalDate.of(2026,10,10));
        for(String valor:List.of("0","-1","300.01","1.001"))
            assertThatThrownBy(()->recebimentos.realizarPagamento(c.getId(),FormaPagamento.PIX,aluno.getId(),new BigDecimal(valor),null)).isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(()->recebimentos.realizarPagamento(c.getId(),FormaPagamento.PIX,aluno.getId(),null,"invalida")).isInstanceOf(RegraNegocioException.class);
        assertThat(repository.count()).isZero();
    }
    @Test void propriedadeEhVerificadaAntesDeRetornarRecibosOuReplays() {
        var c=cobrar(LocalDate.of(2026,10,10)); var outro=novoAluno("Outro"); String chave=UUID.randomUUID().toString();
        recebimentos.realizarPagamento(c.getId(),FormaPagamento.PIX,aluno.getId(),null,chave);
        assertThatThrownBy(()->recebimentos.realizarPagamento(c.getId(),FormaPagamento.PIX,outro.getId(),null,chave)).isInstanceOf(RecursoNaoEncontradoException.class);
        assertThatThrownBy(()->recebimentos.buscarRecibo(c.getId(),outro.getId())).isInstanceOf(RecursoNaoEncontradoException.class);
        assertThatThrownBy(()->recebimentos.listarPorConta(c.getId(),outro.getId())).isInstanceOf(RecursoNaoEncontradoException.class);
    }
    @Test void arquivadoNaoPodeReceberNovosLancamentos() {
        var c=cobrar(LocalDate.of(2026,10,10)); aluno.setArquivado(true); em.flush();
        assertThatThrownBy(()->recebimentos.realizarPagamento(c.getId(),FormaPagamento.PIX,aluno.getId(),new BigDecimal("100"),null)).isInstanceOf(ConflitoException.class);
        assertThatCode(()->contas.gerarRecorrenteSeNecessaria(aluno.getId())).doesNotThrowAnyException();
        assertThat(repository.count()).isZero();
    }
    @Test void protegeValorAlunoExclusaoEStatusManual() {
        var c=cobrar(LocalDate.of(2026,10,10)); recebimentos.realizarPagamento(c.getId(),FormaPagamento.PIX,aluno.getId(),new BigDecimal("100"),null);
        papel("ADMIN");
        assertThatThrownBy(()->contas.deletarContaReceberPorId(c.getId())).isInstanceOf(ConflitoException.class);
        var entrada=new ContaReceber(); entrada.setAluno(aluno); entrada.setValor(new BigDecimal("200")); entrada.setDataVencimento(c.getDataVencimento());
        assertThatThrownBy(()->contas.atualizarContaReceber(c.getId(),entrada)).isInstanceOf(ConflitoException.class);
        entrada.setValor(c.getValor()); entrada.setAluno(novoAluno("Destino"));
        assertThatThrownBy(()->contas.atualizarContaReceber(c.getId(),entrada)).isInstanceOf(ConflitoException.class);
        entrada.setAluno(aluno); entrada.setStatus(StatusFinanceiro.PAGO);
        assertThatThrownBy(()->contas.atualizarContaReceber(c.getId(),entrada)).isInstanceOf(RegraNegocioException.class);
    }
    @Test void competenciaPodeSerCorrigidaPreservandoRecibos() {
        var c=cobrar(LocalDate.of(2026,11,5)); recebimentos.realizarPagamento(c.getId(),FormaPagamento.PIX,aluno.getId(),new BigDecimal("100"),null);
        var entrada=new ContaReceber(); entrada.setAluno(aluno); entrada.setValor(c.getValor()); entrada.setDataVencimento(c.getDataVencimento());
        entrada.setCompetencia(LocalDate.of(2026,10,1)); entrada.setDescricao("Mensalidade outubro"); papel("ADMIN");
        var r=contas.atualizarResposta(c.getId(),entrada);
        assertThat(r.competencia()).isEqualTo("2026-10"); assertThat(r.saldoRestante()).isEqualByComparingTo("200");
        assertThat(repository.totalRecebido(c.getId())).isEqualByComparingTo("100");
    }
    @Test void recorrenciaMantemDia31EmFevereiroMarcoBissextoEViradaDoAno() {
        for(LocalDate origem:List.of(LocalDate.of(2027,1,31),LocalDate.of(2028,1,31),LocalDate.of(2026,12,31))) {
            aluno=novoAluno("Calendário "+origem); when(dashboardClock.instant()).thenReturn(origem.atTime(12,0).atZone(ZoneId.of("America/Sao_Paulo")).toInstant());
            cobrar(origem); contas.gerarRecorrenteSeNecessaria(aluno.getId()); contas.gerarRecorrenteSeNecessaria(aluno.getId());
            var lista=em.createQuery("select c from ContaReceber c where c.aluno.id=:id order by c.dataVencimento",ContaReceber.class).setParameter("id",aluno.getId()).getResultList();
            assertThat(lista).hasSize(2); assertThat(lista.getLast().getDataVencimento()).isEqualTo(origem.plusMonths(1));
            when(dashboardClock.instant()).thenReturn(origem.plusMonths(1).atTime(12,0).atZone(ZoneId.of("America/Sao_Paulo")).toInstant());
            contas.gerarRecorrenteSeNecessaria(aluno.getId());
            var ultima=em.createQuery("select c from ContaReceber c where c.aluno.id=:id order by c.dataVencimento desc limit 1",ContaReceber.class).setParameter("id",aluno.getId()).getSingleResult();
            assertThat(ultima.getDataVencimento()).isEqualTo(origem.plusMonths(2));
        }
    }
    @Test void duplicidadeDeCompetenciaEhRejeitadaSemApagarHistorico() {
        cobrar(LocalDate.of(2026,10,10));
        assertThatThrownBy(()->cobrar(LocalDate.of(2026,10,20))).isInstanceOf(ConflitoException.class);
    }
    @Test void inadimplenciaDashboardEFluxoUsamSaldoEEntradasEfetivas() {
        var c=cobrar(LocalDate.of(2026,10,10)); recebimentos.realizarPagamento(c.getId(),FormaPagamento.PIX,aluno.getId(),new BigDecimal("100"),null);
        papel("ADMIN");
        assertThat((BigDecimal)consultasDashboard.resumoContas(DashboardConsultasRepository.TipoConta.RECEBER,LocalDate.of(2026,10,16))[1]).isEqualByComparingTo("200");
        var r=relatorios.inadimplencia(null,null,null);
        assertThat(r.totalGeral()).isEqualByComparingTo("200"); assertThat(r.alunos().getFirst().cobrancas().getFirst().valorRecebido()).isEqualByComparingTo("100");
        assertThat(consultasDashboard.recebimentos(LocalDate.of(2026,10,1),LocalDate.of(2026,11,1)).get(YearMonth.of(2026,10))).isEqualByComparingTo("100");
    }
    @Test void legadaQuitadaSemComprovanteNaoViraEntradaFicticia() {
        var c=cobrar(LocalDate.of(2026,10,10)); c.setStatus(StatusFinanceiro.PAGO); em.flush();
        var r=contas.listarMinhasContas(aluno.getId()).getFirst();
        assertThat(r.saldoRestante()).isZero(); assertThat(r.valorRecebido()).isZero(); assertThat(r.avisos()).contains("QUITACAO_SEM_COMPROVANTE");
        assertThatThrownBy(()->recebimentos.realizarPagamento(c.getId(),FormaPagamento.PIX,aluno.getId())).isInstanceOf(ConflitoException.class);
    }
    @Test void httpPreservaContratoAntigoEPermiteParcelasHistoricoEDashboardDoAluno() throws Exception {
        var c=cobrar(LocalDate.of(2026,10,10)); String token=token(aluno); String chave=UUID.randomUUID().toString();
        mvc.perform(post("/recebimentos/realizar/{id}",c.getId()).header("Authorization",token).header("Idempotency-Key",chave)
                .param("formaPagamento","PIX").param("valorPago","100")).andExpect(status().isOk()).andExpect(jsonPath("$.valor").value(100));
        mvc.perform(get("/recebimentos/conta/{id}",c.getId()).header("Authorization",token)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/dashboard/aluno").header("Authorization",token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.financeiro.emAberto.valor").value(200)).andExpect(jsonPath("$.financeiro.cobrancaPrincipal.recebimentoParcial").value(true));
        mvc.perform(post("/recebimentos/realizar/{id}",c.getId()).header("Authorization",token).param("formaPagamento","PIX"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.valor").value(200));
        mvc.perform(get("/recebimentos/conta/{id}",c.getId()).header("Authorization",token)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
    }
    @Test void historicoNaoVazaEAdministracaoContinuaExclusiva() throws Exception {
        var c=cobrar(LocalDate.of(2026,10,10)); String outroToken=token(novoAluno("Outro"));
        mvc.perform(get("/recebimentos/conta/{id}",c.getId()).header("Authorization",outroToken)).andExpect(status().isNotFound());
        mvc.perform(get("/contareceber/buscar-contareceber/{id}",c.getId()).header("Authorization",outroToken)).andExpect(status().isForbidden());
        mvc.perform(get("/recebimentos/conta/{id}",c.getId()).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous())).andExpect(status().isUnauthorized());
    }
    @Test void geraPdfDeInadimplenciaComPagamentoParcial() throws Exception {
        var c=cobrar(LocalDate.of(2026,10,10)); recebimentos.realizarPagamento(c.getId(),FormaPagamento.PIX,aluno.getId(),new BigDecimal("100"),null);
        papel("ADMIN"); Path pasta=Path.of("target/contas-receber-exemplos"); Files.createDirectories(pasta);
        Files.write(pasta.resolve("inadimplencia-parcial.pdf"),pdf.inadimplencia(null,null,null));
    }
}
