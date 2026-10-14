package com.example.integrador;

import com.example.integrador.Config.DashboardClockConfig;
import com.example.integrador.DTO.*;
import com.example.integrador.Email.*;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:arquivamento_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
    "app.email.enabled=true", "app.email.production=true", "app.email.from=transporte@example.test",
    "app.email.smtp-host=smtp.example.test", "app.email.schedulers-enabled=false"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ArquivamentoAlunoIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager manager;
    @Autowired AlunoService alunos;
    @Autowired PessoaService pessoas;
    @Autowired UsuarioService usuarios;
    @Autowired AuthService auth;
    @Autowired TokenService tokenService;
    @Autowired org.springframework.security.oauth2.jwt.JwtDecoder decoder;
    @Autowired org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter converter;
    @Autowired PasswordEncoder encoder;
    @Autowired ContaReceberService cobrancas;
    @Autowired ContaPagarService contasPagar;
    @Autowired RecebimentoService recebimentos;
    @Autowired RotinaFinanceiraService rotina;
    @Autowired PessoaBloqueioService bloqueios;
    @Autowired DashboardService dashboard;
    @Autowired ViagemService viagens;
    @Autowired AlunoRepository alunoRepository;
    @Autowired EmailLoteService lotes;
    @Autowired EmailFilaService fila;
    @Autowired ChamadoManutencaoService chamados;
    @MockitoBean Clock dashboardClock;
    @MockitoBean EmailGateway gateway;
    private TransactionTemplate tx;
    private Aluno aluno, outro;
    private Usuario admin, acesso, acessoMotorista;
    private Faculdade faculdade;
    private Onibus onibus;
    private Motorista motorista;
    private Instant agora;
    private String adminToken, alunoToken, motoristaToken;

    @BeforeEach
    void preparar() {
        SecurityContextHolder.clearContext();
        reset(gateway);
        agora = Instant.parse("2026-10-12T18:00:00Z");
        when(dashboardClock.instant()).thenAnswer(i -> agora);
        when(dashboardClock.getZone()).thenReturn(DashboardClockConfig.ZONA);
        tx = new TransactionTemplate(manager);
        tx.executeWithoutResult(s -> {
            for (String entidade : List.of("EmailEntrega", "EmailLote", "Recebimento", "Pagamento", "ContaReceber", "ContaPagar", "ChamadoManutencao"))
                em.createQuery("delete " + entidade).executeUpdate();
            em.createNativeQuery("delete from viagem_faculdade").executeUpdate();
            em.createQuery("delete Viagem").executeUpdate();
            em.createNativeQuery("delete from rota_pontos_embarque").executeUpdate();
            for (String entidade : List.of("PontoEmbarque", "Rota", "GradeDiaria", "Usuario", "Pessoa", "Faculdade", "Onibus"))
                em.createQuery("delete " + entidade).executeUpdate();
            faculdade = new Faculdade(); faculdade.setNome("Faculdade de teste"); em.persist(faculdade);
            aluno = novoAluno("Aluno", false);
            outro = novoAluno("Outro", false);
            Pessoa pessoaAdmin = new Pessoa(); pessoaAdmin.setNome("Admin"); pessoaAdmin.setTipoPessoa(TipoPessoa.ADMIN); em.persist(pessoaAdmin);
            admin = acesso(pessoaAdmin, "admin@arquivo.test", TipoPessoa.ADMIN);
            acesso = acesso(aluno, "aluno@arquivo.test", TipoPessoa.ALUNO);
            motorista = new Motorista(); motorista.setNome("Motorista"); motorista.setTipoPessoa(TipoPessoa.MOTORISTA); em.persist(motorista);
            acessoMotorista = acesso(motorista, "motorista@arquivo.test", TipoPessoa.MOTORISTA);
            onibus = new Onibus(); onibus.setNumeroIdentificacao("01"); onibus.setCapacidade(1); onibus.setStatusOnibus(StatusOnibus.OPERANDO); em.persist(onibus);
            var config = em.find(EmailConfiguracao.class, 1L); config.setAutomatico(false); config.setUltimaReserva(null);
        });
        adminToken = token(admin.getEmail()); alunoToken = token(acesso.getEmail()); motoristaToken = token(acessoMotorista.getEmail());
        SecurityContextHolder.getContext().setAuthentication(converter.convert(decoder.decode(adminToken.substring(7))));
    }
    @AfterEach void limpar() { SecurityContextHolder.clearContext(); }

    @Test void arquivarRevogaAcessoPreservaMatriculaEIdempotencia() {
        alunos.arquivarAluno(aluno.getId(), true);
        assertThat(alunoAtual().isArquivado()).isTrue();
        assertThat(alunoAtual().getStatusMatricula()).isEqualTo(StatusMatricula.ATIV0);
        assertThat(usuarioAtual().isAtivo()).isFalse();
        assertThat(usuarioAtual().getTokenVersion()).isEqualTo(1);
        alunos.arquivarAluno(aluno.getId(), true);
        assertThat(usuarioAtual().getTokenVersion()).isEqualTo(1);
        assertThat(alunoAtual().getFaculdade().getId()).isEqualTo(faculdade.getId());
    }

    @Test void arquivarSemUsuarioERestaurarNaoCriaCobranca() {
        alunos.arquivarAluno(outro.getId(), true);
        alunos.arquivarAluno(outro.getId(), false);
        assertThat(alunos.consultarSituacaoExclusao(outro.getId()).possuiAcesso()).isFalse();
        assertThat(contar("ContaReceber")).isZero();
    }

    @Test void restaurarNaoReativaMasRepeticaoNaoDesativaContaReativadaDepois() {
        alunos.arquivarAluno(aluno.getId(), true);
        alunos.arquivarAluno(aluno.getId(), false);
        assertThat(usuarioAtual().isAtivo()).isFalse();
        usuarios.atualizarStatus(acesso.getId(), new AtualizarStatusUsuarioRequest(true), admin.getId());
        alunos.arquivarAluno(aluno.getId(), false);
        assertThat(usuarioAtual().isAtivo()).isTrue();
        assertThat(usuarioAtual().getTokenVersion()).isEqualTo(2);
    }

    @Test void inconsistenciasDeAcessoSaoCorrigidasSemRevogacaoInfinita() {
        alunos.arquivarAluno(aluno.getId(), true);
        ativarInconsistente();
        alunos.arquivarAluno(aluno.getId(), true);
        assertThat(usuarioAtual().isAtivo()).isFalse();
        assertThat(usuarioAtual().getTokenVersion()).isEqualTo(2);
        ativarInconsistente();
        alunos.arquivarAluno(aluno.getId(), false);
        assertThat(usuarioAtual().isAtivo()).isFalse();
        assertThat(usuarioAtual().getTokenVersion()).isEqualTo(3);
    }

    @Test void filtrosListagemEDetalheDeArquivado() throws Exception {
        alunos.arquivarAluno(aluno.getId(), true);
        mvc.perform(get("/aluno/listar").header("Authorization", adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].arquivado").value(false));
        mvc.perform(get("/aluno/listar").param("situacao", "ARQUIVADOS").header("Authorization", adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(aluno.getId()));
        mvc.perform(get("/aluno/listar").param("situacao", "TODOS").header("Authorization", adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/aluno/listar").param("situacao", "ATIVO").header("Authorization", adminToken)).andExpect(status().isBadRequest());
        mvc.perform(get("/aluno/buscar-aluno/" + aluno.getId()).header("Authorization", adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.arquivado").value(true));
    }

    @Test void patchExigeBooleanRealERejeitaCamposExtras() throws Exception {
        for (String corpo : List.of("{}", "{\"arquivado\":null}", "{\"arquivado\":\"true\"}", "{\"arquivado\":1}", "{\"arquivado\":[]}", "{\"arquivado\":true,\"adminId\":1}")) {
            mvc.perform(patch("/aluno/" + aluno.getId() + "/arquivamento").header("Authorization", adminToken)
                    .contentType("application/json").content(corpo)).andExpect(status().isBadRequest());
        }
        mvc.perform(patch("/aluno/" + aluno.getId() + "/arquivamento").header("Authorization", adminToken)
                .contentType("application/json").content("{\"arquivado\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.arquivado").value(true))
                .andExpect(jsonPath("$.senha").doesNotExist()).andExpect(jsonPath("$.tokenVersion").doesNotExist());
        mvc.perform(patch("/aluno/99999999/arquivamento").header("Authorization", adminToken)
                .contentType("application/json").content("{\"arquivado\":false}")).andExpect(status().isNotFound());
    }

    @Test void autenticacaoEPermissoesDosEndpointsEServicos() throws Exception {
        SecurityContextHolder.clearContext();
        String patchUrl = "/aluno/" + aluno.getId() + "/arquivamento";
        for (String jwt : List.of(alunoToken, motoristaToken)) {
            mvc.perform(patch(patchUrl).header("Authorization", jwt).contentType("application/json")
                    .content("{\"arquivado\":true}")).andExpect(status().isForbidden());
            mvc.perform(get("/aluno/" + aluno.getId() + "/situacao-exclusao").header("Authorization", jwt)).andExpect(status().isForbidden());
            mvc.perform(delete("/aluno/deletar-aluno/" + aluno.getId()).header("Authorization", jwt)).andExpect(status().isForbidden());
        }
        mvc.perform(patch(patchUrl).contentType("application/json").content("{\"arquivado\":true}")).andExpect(status().isUnauthorized());
        mvc.perform(get("/aluno/listar")).andExpect(status().isUnauthorized());
        mvc.perform(get("/aluno/" + aluno.getId() + "/situacao-exclusao")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/aluno/deletar-aluno/" + aluno.getId())).andExpect(status().isUnauthorized());
        papel("ALUNO");
        assertThatThrownBy(() -> alunos.arquivarAluno(aluno.getId(), true)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> alunos.listarTodosAlunos()).isInstanceOf(AccessDeniedException.class);
    }

    @Test void exclusaoExigeArquivamentoEContaNaoEhImpedimento() throws Exception {
        var situacao = alunos.consultarSituacaoExclusao(aluno.getId());
        assertThat(situacao.possuiAcesso()).isTrue();
        assertThat(situacao.podeExcluir()).isFalse();
        mvc.perform(delete("/aluno/deletar-aluno/" + aluno.getId()).header("Authorization", adminToken))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("ALUNO_NAO_ARQUIVADO"));
        papel("ADMIN"); alunos.arquivarAluno(aluno.getId(), true);
        assertThat(alunos.consultarSituacaoExclusao(aluno.getId()).podeExcluir()).isTrue();
        mvc.perform(delete("/aluno/deletar-aluno/" + aluno.getId()).header("Authorization", adminToken))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        assertThat(tx.execute(s -> em.find(Usuario.class, acesso.getId())) == null).isTrue();
        mvc.perform(delete("/aluno/deletar-aluno/" + aluno.getId()).header("Authorization", adminToken)).andExpect(status().isNotFound());
    }

    @Test void exclusaoSemAcessoEGenericaUsamMesmaPolitica() {
        assertThatThrownBy(() -> pessoas.deletarPessoaPorId(outro.getId())).isInstanceOf(ExclusaoAlunoBloqueadaException.class);
        alunos.arquivarAluno(outro.getId(), true);
        pessoas.deletarPessoaPorId(outro.getId());
        assertThat(tx.execute(s -> em.find(Pessoa.class, outro.getId())) == null).isTrue();
    }

    @Test void vinculosContamTodosEstadosSemMultiplicarENaOrdemDefinida() {
        tx.executeWithoutResult(s -> {
            conta(aluno, StatusFinanceiro.PAGO, LocalDate.now().plusDays(5));
            conta(aluno, StatusFinanceiro.PENDENTE, LocalDate.now().plusDays(6));
            ContaPagar cp = new ContaPagar(); cp.setMotorista(em.find(Aluno.class, aluno.getId())); cp.setStatus(StatusFinanceiro.PAGO); em.persist(cp);
            chamado(aluno, StatusChamado.RESOLVIDO);
            Viagem v = new Viagem(); em.persist(v); em.flush();
            em.createNativeQuery("update viagem set motorista_id = :aluno where id = :id").setParameter("aluno", aluno.getId()).setParameter("id", v.getId()).executeUpdate();
        });
        var situacao = alunos.consultarSituacaoExclusao(aluno.getId());
        assertThat(situacao.vinculos().contasReceber()).isEqualTo(2);
        assertThat(situacao.vinculos().contasPagar()).isEqualTo(1);
        assertThat(situacao.vinculos().chamados()).isEqualTo(1);
        assertThat(situacao.vinculos().viagens()).isEqualTo(1);
        assertThat(situacao.impedimentos()).extracting(i -> i.codigo())
                .containsExactly("ALUNO_NAO_ARQUIVADO", "CONTAS_RECEBER", "CONTAS_PAGAR", "CHAMADOS", "VIAGENS");
    }

    @Test void cobrancasDeQualquerEstadoImpedemExclusaoEConflitoEstruturado() throws Exception {
        for (StatusFinanceiro estado : StatusFinanceiro.values()) {
            tx.executeWithoutResult(s -> conta(aluno, estado, LocalDate.now().minusDays(5 + estado.ordinal())));
        }
        alunos.arquivarAluno(aluno.getId(), true);
        mvc.perform(delete("/aluno/deletar-aluno/" + aluno.getId()).header("Authorization", adminToken))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("ALUNO_POSSUI_VINCULOS"))
                .andExpect(jsonPath("$.vinculos.contasReceber").value(StatusFinanceiro.values().length));
        assertThat(usuarioAtual()).isNotNull();
    }

    @Test void chamadoAbertoOuResolvidoImpedeExclusao() {
        tx.executeWithoutResult(s -> { chamado(aluno, StatusChamado.ABERTO); chamado(aluno, StatusChamado.RESOLVIDO); });
        alunos.arquivarAluno(aluno.getId(), true);
        assertThatThrownBy(() -> pessoas.deletarPessoaPorId(aluno.getId())).isInstanceOf(ExclusaoAlunoBloqueadaException.class);
        assertThat(alunos.consultarSituacaoExclusao(aluno.getId()).vinculos().chamados()).isEqualTo(2);
    }

    @Test void loginEJwtBloqueadosMesmoComUsuarioInconsistente() throws Exception {
        alunos.arquivarAluno(aluno.getId(), true);
        ativarInconsistente();
        mvc.perform(post("/auth/login").contentType("application/json")
                .content("{\"email\":\"aluno@arquivo.test\",\"senha\":\"SenhaTeste123\"}")).andExpect(status().isUnauthorized());
        mvc.perform(get("/auth/me").header("Authorization", alunoToken)).andExpect(status().isUnauthorized());
        // Forjar somente uma nova versão com o emissor do teste ainda não permite usar JWT de arquivado.
        var usuario = usuarioAtual();
        String novo = "Bearer " + tokenService.emitir(usuario).valor();
        mvc.perform(get("/auth/me").header("Authorization", novo)).andExpect(status().isUnauthorized());
    }

    @Test void criarEReativarContaDeArquivadoEhBloqueadoInclusiveIdempotencia() {
        alunos.arquivarAluno(outro.getId(), true);
        assertThat(usuarios.listarPessoasSemUsuario()).extracting(PessoaDisponivelResponse::id).doesNotContain(outro.getId());
        assertThatThrownBy(() -> usuarios.criarUsuario(new CriarUsuarioRequest(outro.getId(), "novo@arquivo.test", "SenhaTeste123"))).isInstanceOf(ConflitoException.class);
        alunos.arquivarAluno(aluno.getId(), true);
        ativarInconsistente();
        assertThatThrownBy(() -> usuarios.atualizarStatus(acesso.getId(), new AtualizarStatusUsuarioRequest(true), admin.getId())).isInstanceOf(ConflitoException.class);
    }

    @Test void manterEmailESenhaNaoReativaEExcluirAcessoPreservaArquivamento() {
        alunos.arquivarAluno(aluno.getId(), true);
        usuarios.atualizarEmail(acesso.getId(), new AtualizarEmailUsuarioRequest("novo@arquivo.test"));
        usuarios.redefinirSenha(acesso.getId(), new RedefinirSenhaUsuarioRequest("OutraSenha123"));
        assertThat(usuarioAtual().isAtivo()).isFalse();
        usuarios.excluirAcesso(acesso.getId(), admin.getId());
        assertThat(alunoAtual().isArquivado()).isTrue();
    }

    @Test void edicaoComumNaoDesarquivaENaoGeraCobrancaAusente() throws Exception {
        alunos.arquivarAluno(aluno.getId(), true);
        mvc.perform(put("/aluno/atualizar-aluno/" + aluno.getId()).header("Authorization", adminToken)
                .contentType("application/json").content("{\"nome\":\"Nome editado\",\"mensalidade\":200,\"arquivado\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.arquivado").value(true));
        assertThat(contar("ContaReceber")).isZero();
        papel("ADMIN");
        Aluno entrada = new Aluno(); entrada.setNome("Pessoa editada"); entrada.setArquivado(false);
        pessoas.atualizarPessoa(aluno.getId(), entrada);
        assertThat(alunoAtual().isArquivado()).isTrue();
    }

    @Test void cadastroNormalCriaPrimeiraContaEPessoaGenericaNaoPodeCriarArquivado() {
        AlunoDTO dto = new AlunoDTO(); dto.setNome("Novo"); dto.setMensalidade(new BigDecimal("150"));
        var novo = alunos.salvarAluno(dto);
        assertThat(novo.isArquivado()).isFalse();
        assertThat(alunos.consultarSituacaoExclusao(novo.getId()).vinculos().contasReceber()).isEqualTo(1);
        Aluno generico = new Aluno(); generico.setArquivado(true); generico.setNome("Genérico");
        var salvo = (Aluno) pessoas.salvarPessoa(generico);
        assertThat(salvo.isArquivado()).isFalse();
    }

    @Test void nenhumGeradorDiretoCriaMensalidadeParaArquivado() {
        alunos.arquivarAluno(aluno.getId(), true);
        assertThatThrownBy(() -> cobrancas.gerarPrimeiraConta(aluno)).isInstanceOf(ConflitoException.class);
        assertThatThrownBy(() -> cobrancas.gerarPrimeiraContaSeAusente(aluno)).isInstanceOf(ConflitoException.class);
        assertThatThrownBy(() -> cobrancas.gerarProximaConta(aluno, LocalDate.now().plusDays(30))).isInstanceOf(ConflitoException.class);
        assertThatThrownBy(() -> cobrancas.salvarContaReceber(novaConta(aluno, StatusFinanceiro.PENDENTE, LocalDate.now()))).isInstanceOf(ConflitoException.class);
        ContaPagar cp = new ContaPagar(); cp.setMotorista(aluno);
        assertThatThrownBy(() -> contasPagar.salvarContaPagar(cp)).isInstanceOf(ConflitoException.class);
        assertThat(contar("ContaReceber")).isZero();
    }

    @Test void rotinaIgnoraArquivadoERestauracaoNaoReiniciaPeriodo() {
        tx.executeWithoutResult(s -> conta(aluno, StatusFinanceiro.PENDENTE, LocalDate.now().minusDays(90)));
        alunos.arquivarAluno(aluno.getId(), true);
        rotina.gerarContasRecorrentes();
        assertThat(alunos.consultarSituacaoExclusao(aluno.getId()).vinculos().contasReceber()).isEqualTo(1);
        alunos.arquivarAluno(aluno.getId(), false);
        assertThat(alunos.consultarSituacaoExclusao(aluno.getId()).vinculos().contasReceber()).isEqualTo(1);
        rotina.gerarContasRecorrentes();
        assertThat(alunos.consultarSituacaoExclusao(aluno.getId()).vinculos().contasReceber()).isEqualTo(2);
    }

    @Test void editarHistoricoEhPermitidoMasTrocarProprietarioParaArquivadoNao() {
        Long id = tx.execute(s -> conta(outro, StatusFinanceiro.PENDENTE, LocalDate.now().plusDays(30)).getId());
        alunos.arquivarAluno(aluno.getId(), true);
        assertThatThrownBy(() -> cobrancas.atualizarContaReceber(id, novaConta(aluno, StatusFinanceiro.PENDENTE, LocalDate.now()))).isInstanceOf(ConflitoException.class);
        Long historico = tx.execute(s -> conta(aluno, StatusFinanceiro.PAGO, LocalDate.now()).getId());
        cobrancas.atualizarContaReceber(historico, novaConta(aluno, StatusFinanceiro.PAGO, LocalDate.now()));
        assertThat(alunos.consultarSituacaoExclusao(aluno.getId()).vinculos().contasReceber()).isEqualTo(1);
    }

    @Test void pagamentoBloqueadoOcultaCobrancaAlheiaEPreservaHistorico() {
        Long minha = tx.execute(s -> conta(aluno, StatusFinanceiro.PENDENTE, LocalDate.now()).getId());
        Long alheia = tx.execute(s -> conta(outro, StatusFinanceiro.PENDENTE, LocalDate.now()).getId());
        alunos.arquivarAluno(aluno.getId(), true);
        papel("ALUNO");
        assertThatThrownBy(() -> recebimentos.realizarPagamento(alheia, FormaPagamento.PIX, aluno.getId())).isInstanceOf(RecursoNaoEncontradoException.class);
        assertThatThrownBy(() -> recebimentos.realizarPagamento(minha, FormaPagamento.PIX, aluno.getId())).isInstanceOf(ConflitoException.class);
        assertThat(contar("Recebimento")).isZero();
    }

    @Test void financeiroEChamadosPagosSaoPreservadosComIndicadoresFiltrados() {
        Long conta = tx.execute(s -> {
            chamado(aluno, StatusChamado.RESOLVIDO);
            return conta(aluno, StatusFinanceiro.PENDENTE, LocalDate.now()).getId();
        });
        papel("ALUNO"); recebimentos.realizarPagamento(conta, FormaPagamento.PIX, aluno.getId());
        papel("ADMIN");
        var antes = dashboard.admin(null);
        alunos.arquivarAluno(aluno.getId(), true);
        var depois = dashboard.admin(null);
        assertThat(depois.operacao().alunos().total()).isEqualTo(antes.operacao().alunos().total() - 1);
        assertThat(depois.financeiro()).isEqualTo(antes.financeiro());
        assertThat(contar("Recebimento")).isEqualTo(1);
        assertThat(contar("ChamadoManutencao")).isEqualTo(1);
        assertThat(alunoRepository.countByFaculdadeIdAndStatusMatriculaAndArquivadoFalse(faculdade.getId(), StatusMatricula.ATIV0)).isEqualTo(1);
    }

    @Test void criarChamadoDeAlunoArquivadoEhBloqueado() {
        alunos.arquivarAluno(aluno.getId(), true);
        ativarInconsistente();
        // Simula requisição autenticada antes do arquivamento: revalidação do serviço ainda impede o vínculo.
        var jwt = org.springframework.security.oauth2.jwt.Jwt.withTokenValue("teste").header("alg", "HS256")
                .subject(acesso.getId().toString()).build();
        var authJwt = new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("ROLE_ALUNO")));
        SecurityContextHolder.getContext().setAuthentication(authJwt);
        assertThatThrownBy(() -> chamados.abrir(new AbrirChamadoRequest(onibus.getId(), "Relato"))).isInstanceOf(ConflitoException.class);
    }

    @Test void emailSelecionaNaoArquivadosESuprimeFilaMesmoComAcessoInconsistente() {
        tx.executeWithoutResult(s -> viagemDoDia());
        var lote = lotes.solicitar(new EmailDto.EnvioRequest(UUID.randomUUID(), false, null));
        alunos.arquivarAluno(aluno.getId(), true);
        ativarInconsistente();
        assertThat(lotes.previa().destinatariosElegiveis()).isEqualTo(1);
        fila.processarUma(); agora = agora.plusSeconds(1); fila.processarUma();
        assertThat(lotes.detalhe(lote.id()).entregas().stream().filter(e -> e.usuarioId().equals(acesso.getId())).findFirst().orElseThrow().estado())
                .isEqualTo(EmailDto.Estado.SUPRIMIDA);
        verify(gateway, times(1)).enviar(any(), eq("motorista@arquivo.test"), any());
        alunos.arquivarAluno(aluno.getId(), false);
        fila.processarUma();
        verify(gateway, times(1)).enviar(any(), anyString(), any());
    }

    @Test void excluirAlunoSemVinculosPreservaHistoricoDeEmail() {
        tx.executeWithoutResult(s -> viagemDoDia()); // Viagem pertence ao motorista, não ao aluno.
        var lote = lotes.solicitar(new EmailDto.EnvioRequest(UUID.randomUUID(), false, null));
        alunos.arquivarAluno(aluno.getId(), true);
        alunos.deletarAlunoPorId(aluno.getId());
        assertThat(lotes.detalhe(lote.id()).entregas()).hasSize(2);
        assertThat(lotes.detalhe(lote.id()).mensagem()).isEqualTo(lote.mensagem());
    }

    @Test void falhaAoArquivarReverteAlunoAcessoEVersao() {
        sql("alter table usuario add constraint teste_usuario_ativo check (ativo = true)");
        try {
            assertThatThrownBy(() -> alunos.arquivarAluno(aluno.getId(), true)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            assertThat(alunoAtual().isArquivado()).isFalse();
            assertThat(usuarioAtual().isAtivo()).isTrue();
            assertThat(usuarioAtual().getTokenVersion()).isZero();
        } finally { sql("alter table usuario drop constraint teste_usuario_ativo"); }
    }

    @Test void falhaDeFkDepoisDeRemoverAcessoFazRollbackIntegral() {
        alunos.arquivarAluno(aluno.getId(), true);
        sql("create table teste_vinculo_extra (id bigint primary key, pessoa_id bigint references pessoa(id))");
        sql("insert into teste_vinculo_extra values (1," + aluno.getId() + ")");
        try {
            assertThat(alunos.consultarSituacaoExclusao(aluno.getId()).podeExcluir()).isTrue();
            assertThatThrownBy(() -> alunos.deletarAlunoPorId(aluno.getId())).isInstanceOfSatisfying(ExclusaoAlunoBloqueadaException.class,
                    e -> { assertThat(e.getVinculos()).isNull(); assertThat(e.getMessage()).doesNotContain("SQL", "teste_vinculo"); });
            assertThat(usuarioAtual()).isNotNull();
            assertThat(alunoAtual().isArquivado()).isTrue();
        } finally { sql("drop table teste_vinculo_extra"); }
    }

    @Test void concorrenciaArquivarAntesImpedePagamentoCobrancaEReativacao() throws Exception {
        Long conta = tx.execute(s -> conta(aluno, StatusFinanceiro.PENDENTE, LocalDate.now()).getId());
        CountDownLatch bloqueado = new CountDownLatch(1), liberar = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(4)) {
            Future<?> arquivo = executor.submit(() -> { papel("ADMIN"); tx.executeWithoutResult(s -> {
                alunos.arquivarAluno(aluno.getId(), true); bloqueado.countDown(); aguardar(liberar);
            }); SecurityContextHolder.clearContext(); });
            assertThat(bloqueado.await(10, TimeUnit.SECONDS)).isTrue();
            Future<?> pagar = executor.submit(() -> { papel("ALUNO"); recebimentos.realizarPagamento(conta, FormaPagamento.PIX, aluno.getId()); });
            Future<?> cobrar = executor.submit(() -> cobrancas.gerarProximaConta(aluno, LocalDate.now().plusDays(30)));
            Future<?> reativar = executor.submit(() -> usuarios.atualizarStatus(acesso.getId(), new AtualizarStatusUsuarioRequest(true), admin.getId()));
            liberar.countDown();
            arquivo.get(15, TimeUnit.SECONDS);
            for (Future<?> futuro : List.of(pagar, cobrar, reativar))
                assertThatThrownBy(() -> futuro.get(15, TimeUnit.SECONDS)).hasCauseInstanceOf(ConflitoException.class);
        } finally { liberar.countDown(); }
        assertThat(usuarioAtual().isAtivo()).isFalse();
        assertThat(contar("ContaReceber")).isEqualTo(1);
        assertThat(contar("Recebimento")).isZero();
    }

    @Test void pagamentoQueConcluiAntesEhPreservadoPeloArquivamento() throws Exception {
        Long conta = tx.execute(s -> conta(aluno, StatusFinanceiro.PENDENTE, LocalDate.now()).getId());
        CountDownLatch bloqueado = new CountDownLatch(1), liberar = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<?> pagar = executor.submit(() -> { papel("ALUNO"); tx.executeWithoutResult(s -> {
                recebimentos.realizarPagamento(conta, FormaPagamento.PIX, aluno.getId()); bloqueado.countDown(); aguardar(liberar);
            }); });
            assertThat(bloqueado.await(10, TimeUnit.SECONDS)).isTrue();
            Future<?> arquivo = executor.submit(() -> { papel("ADMIN"); alunos.arquivarAluno(aluno.getId(), true); });
            liberar.countDown(); pagar.get(15, TimeUnit.SECONDS); arquivo.get(15, TimeUnit.SECONDS);
        } finally { liberar.countDown(); }
        assertThat(alunoAtual().isArquivado()).isTrue();
        assertThat(alunoAtual().getStatusMatricula()).isEqualTo(StatusMatricula.ATIV0);
        assertThat(contar("Recebimento")).isEqualTo(1);
    }

    @Test void vinculoNovoDepoisDaPreviaBloqueiaExclusao() {
        alunos.arquivarAluno(aluno.getId(), true);
        assertThat(alunos.consultarSituacaoExclusao(aluno.getId()).podeExcluir()).isTrue();
        tx.executeWithoutResult(s -> conta(aluno, StatusFinanceiro.PENDENTE, LocalDate.now()));
        assertThatThrownBy(() -> alunos.deletarAlunoPorId(aluno.getId())).isInstanceOf(ExclusaoAlunoBloqueadaException.class);
        assertThat(usuarioAtual()).isNotNull();
    }

    @Test void defaultsValemParaTodosSubtipos() {
        tx.executeWithoutResult(s -> {
            for (Long id : List.of(aluno.getId(), motorista.getId(), admin.getPessoa().getId())) {
                Object flag = em.createNativeQuery("select arquivado from pessoa where id=:id").setParameter("id", id).getSingleResult();
                assertThat(flag).isEqualTo(false);
            }
        });
    }

    @Test void capacidadeDaViagemDesconsideraAlunosArquivados() {
        ViagemDTO dto = new ViagemDTO();
        dto.setOnibusId(onibus.getId());
        dto.setMotoristaId(motorista.getId());
        dto.setFaculdades(new ArrayList<>(List.of(faculdade)));
        assertThatThrownBy(() -> viagens.salvarViagem(dto)).isInstanceOf(RuntimeException.class).hasMessageContaining("capacidade");
        alunos.arquivarAluno(aluno.getId(), true);
        assertThat(viagens.salvarViagem(dto).getId()).isNotNull();
    }

    @Test void cadaVinculoLegadoIsoladoImpedeExclusao() {
        alunos.arquivarAluno(aluno.getId(), true);
        tx.executeWithoutResult(s -> {
            ContaPagar conta = new ContaPagar(); conta.setMotorista(em.find(Aluno.class, aluno.getId()));
            em.persist(conta);
        });
        assertThatThrownBy(() -> alunos.deletarAlunoPorId(aluno.getId())).isInstanceOf(ExclusaoAlunoBloqueadaException.class);
        tx.executeWithoutResult(s -> {
            em.createQuery("delete ContaPagar").executeUpdate();
            Viagem v = new Viagem(); em.persist(v); em.flush();
            em.createNativeQuery("update viagem set motorista_id=:aluno where id=:id")
                    .setParameter("aluno", aluno.getId()).setParameter("id", v.getId()).executeUpdate();
        });
        assertThatThrownBy(() -> alunos.deletarAlunoPorId(aluno.getId())).isInstanceOf(ExclusaoAlunoBloqueadaException.class);
    }

    @Test void exclusaoConcorrenteGanhaDoNovoVinculoSemDeixarAcessoOrfao() throws Exception {
        alunos.arquivarAluno(aluno.getId(), true);
        CountDownLatch excluido = new CountDownLatch(1), liberar = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<?> excluir = executor.submit(() -> {
                papel("ADMIN");
                tx.executeWithoutResult(s -> {
                    alunos.deletarAlunoPorId(aluno.getId());
                    excluido.countDown();
                    aguardar(liberar);
                });
            });
            assertThat(excluido.await(10, TimeUnit.SECONDS)).isTrue();
            Future<?> criar = executor.submit(() -> cobrancas.salvarContaReceber(
                    novaConta(aluno, StatusFinanceiro.PENDENTE, LocalDate.now())));
            liberar.countDown();
            excluir.get(15, TimeUnit.SECONDS);
            assertThatThrownBy(() -> criar.get(15, TimeUnit.SECONDS)).hasCauseInstanceOf(RecursoNaoEncontradoException.class);
        } finally { liberar.countDown(); }
        assertThat(tx.execute(s -> em.find(Aluno.class, aluno.getId())) == null).isTrue();
        assertThat(tx.execute(s -> em.find(Usuario.class, acesso.getId())) == null).isTrue();
        assertThat(contar("ContaReceber")).isZero();
    }

    private void viagemDoDia() {
        GradeDiaria grade = new GradeDiaria(); grade.setDiaSemana(DiaSemana.SEGUNDA); em.persist(grade);
        Viagem v = new Viagem(); v.setGradeDiaria(grade); v.setOnibus(em.find(Onibus.class, onibus.getId()));
        v.setMotorista(em.find(Motorista.class, motorista.getId())); v.setFaculdades(new ArrayList<>(List.of(em.find(Faculdade.class, faculdade.getId())))); em.persist(v);
    }
    private void chamado(Aluno referencia, StatusChamado estado) {
        ChamadoManutencao c = new ChamadoManutencao();
        c.setSolicitante(em.find(Aluno.class, referencia.getId())); c.setOnibus(em.find(Onibus.class, onibus.getId()));
        c.setDescricao("Relato"); c.setDataAbertura(LocalDate.now()); c.setStatus(estado); c.setUrgencia(NivelUrgencia.MEDIA);
        if (estado == StatusChamado.RESOLVIDO) c.setObservacaoAdmin("Resolvido");
        em.persist(c);
    }
    private Aluno novoAluno(String nome, boolean arquivado) {
        Aluno a = new Aluno(); a.setNome(nome); a.setTipoPessoa(TipoPessoa.ALUNO);
        a.setStatusMatricula(StatusMatricula.ATIV0); a.setMensalidade(new BigDecimal("100"));
        a.setFaculdade(faculdade); a.setArquivado(arquivado); em.persist(a); return a;
    }
    private Usuario acesso(Pessoa pessoa, String email, TipoPessoa papel) {
        Usuario u = new Usuario(); u.setPessoa(pessoa); u.setEmail(email); u.setTipoPessoa(papel);
        u.setSenha(encoder.encode("SenhaTeste123")); em.persist(u); return u;
    }
    private ContaReceber novaConta(Aluno a, StatusFinanceiro estado, LocalDate data) {
        ContaReceber c = new ContaReceber(); c.setAluno(a); c.setDescricao("Mensalidade"); c.setValor(new BigDecimal("100"));
        c.setStatus(estado); c.setDataVencimento(data); return c;
    }
    private ContaReceber conta(Aluno a, StatusFinanceiro estado, LocalDate data) {
        var c = novaConta(em.find(Aluno.class, a.getId()), estado, data); em.persist(c); return c;
    }
    private Aluno alunoAtual() { return tx.execute(s -> em.find(Aluno.class, aluno.getId())); }
    private Usuario usuarioAtual() { return tx.execute(s -> em.find(Usuario.class, acesso.getId())); }
    private long contar(String entidade) { return tx.execute(s -> em.createQuery("select count(e) from " + entidade + " e", Long.class).getSingleResult()); }
    private void ativarInconsistente() { sql("update usuario set ativo=true where id=" + acesso.getId()); }
    private void sql(String sql) { tx.executeWithoutResult(s -> em.createNativeQuery(sql).executeUpdate()); }
    private String token(String email) { return "Bearer " + auth.login(new LoginRequest(email, "SenhaTeste123")).accessToken(); }
    private void papel(String papel) { SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("teste", null, List.of(new SimpleGrantedAuthority("ROLE_" + papel)))); }
    private void aguardar(CountDownLatch latch) {
        try { if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Timeout do teste"); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
    }
}
