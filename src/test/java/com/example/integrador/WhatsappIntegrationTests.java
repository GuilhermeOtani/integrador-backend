package com.example.integrador;

import com.example.integrador.DTO.*;
import com.example.integrador.Enum.*;
import com.example.integrador.Model.*;
import com.example.integrador.Repository.UsuarioRepository;
import com.example.integrador.Service.*;
import com.example.integrador.Whatsapp.*;
import com.example.integrador.Whatsapp.WhatsappDto.*;
import com.example.integrador.Whatsapp.WhatsappTipos.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.net.URLDecoder;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:whatsapp;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@AutoConfigureMockMvc
class WhatsappIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired EntityManager em;
    @Autowired TransactionTemplate tx;
    @Autowired UsuarioService usuarios;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired AuthService auth;
    @Autowired JwtDecoder decoder;
    @Autowired JwtAuthenticationConverter converter;
    @Autowired WhatsappProperties properties;
    @Autowired WhatsappDisponibilidadeService disponibilidade;
    @Autowired WhatsappAssinaturaService assinaturas;
    @Autowired WhatsappWebhookService webhook;
    @Autowired WhatsappAdministracaoService administracao;
    @Autowired WhatsappLoteService lotes;
    @Autowired WhatsappFilaService fila;
    @Autowired WhatsappRotinaService rotina;
    @Autowired WhatsappRepos repos;
    @Autowired JsonMapper json;
    @Autowired ProgramacaoWhatsappService programacao;
    @MockitoBean Clock dashboardClock;
    @MockitoBean MetaWhatsappGateway meta;
    protected Long adminId, alunoId, outroAlunoId, motoristaId, pessoaAlunoId, viagemId;
    private String tokenAdmin, tokenAluno, tokenOutro, tokenMotorista;
    private static final String NUMERO = "+5511999990001";
    private static final String NUMERO_OUTRO = "+5511999990002";
    private static final String NUMERO_MOTORISTA = "+5511999990003";
    private static final Instant AGORA = Instant.parse("2026-10-14T18:00:00Z");

    @BeforeEach
    void preparar() {
        SecurityContextHolder.clearContext();
        reset(meta);
        when(dashboardClock.instant()).thenReturn(AGORA);
        properties.setEnabled(true); properties.setSchedulersEnabled(false); properties.setProduction(false);
        properties.setGraphApiVersion("v26.0"); properties.setAccessToken("token-ficticio-testes");
        properties.setWabaId("123"); properties.setPhoneNumberId("456"); properties.setBusinessNumber("+5511999999999");
        properties.setAppSecret("segredo-ficticio-testes"); properties.setVerifyToken("verificacao-ficticia-testes");
        properties.setTemplateName("programacao_transporte"); properties.setTestAllowedNumbers(Set.of(NUMERO, NUMERO_OUTRO, NUMERO_MOTORISTA));
        tx.executeWithoutResult(s -> {
            // O contexto usa exclusivamente banco H2 próprio ou schema PostgreSQL temporário do teste.
            em.createNativeQuery("DELETE FROM viagem_faculdade").executeUpdate();
            em.createNativeQuery("DELETE FROM rota_pontos_embarque").executeUpdate();
            for (String entidade : List.of("WhatsappEntrega", "WhatsappLote", "WhatsappAssinatura", "Recebimento", "Pagamento",
                    "ContaReceber", "ContaPagar", "ChamadoManutencao", "Viagem", "GradeDiaria", "PontoEmbarque", "Rota", "Onibus", "Usuario", "Pessoa", "Faculdade")) {
                em.createQuery("delete from " + entidade).executeUpdate();
            }
            var cfg = em.find(WhatsappConfig.class, 1L); cfg.setAutomatico(false); cfg.setUltimaReserva(null);
            cfg.setWebhookVerificadoEm(null); cfg.setWebhookFingerprint(null); cfg.setTemplateVerificadoEm(null); cfg.setTemplateFingerprint(null);
            adminId = usuarios.criarAdmin(new CriarAdminRequest("Admin", "admin@whatsapp.test", "SenhaTeste123")).id();
            var faculdade = new Faculdade(); faculdade.setNome("Faculdade São João"); faculdade.setEndereco("Rua das Acácias"); em.persist(faculdade);
            var outra = new Faculdade(); outra.setNome("Outra faculdade"); em.persist(outra);
            var aluno = new Aluno(); aluno.setNome("Ana"); aluno.setTipoPessoa(TipoPessoa.ALUNO); aluno.setFaculdade(faculdade);
            aluno.setTelefone("telefone-legado"); aluno.setCpfCnpj("CPF-PRIVADO"); aluno.setStatusMatricula(StatusMatricula.PENDENTE); em.persist(aluno);
            pessoaAlunoId = aluno.getId();
            alunoId = usuarios.criarUsuario(new CriarUsuarioRequest(aluno.getId(), "aluno@whatsapp.test", "SenhaTeste123")).id();
            var outro = new Aluno(); outro.setNome("Outro aluno"); outro.setTipoPessoa(TipoPessoa.ALUNO); outro.setFaculdade(outra); em.persist(outro);
            outroAlunoId = usuarios.criarUsuario(new CriarUsuarioRequest(outro.getId(), "outro@whatsapp.test", "SenhaTeste123")).id();
            var motorista = new Motorista(); motorista.setNome("João"); motorista.setTipoPessoa(TipoPessoa.MOTORISTA); motorista.setCnh("CNH-PRIVADA"); em.persist(motorista);
            motoristaId = usuarios.criarUsuario(new CriarUsuarioRequest(motorista.getId(), "motorista@whatsapp.test", "SenhaTeste123")).id();
            var onibus = new Onibus(); onibus.setPlaca("ABC1234"); onibus.setNumeroIdentificacao("01"); onibus.setModelo("Modelo");
            onibus.setCapacidade(40); onibus.setStatusOnibus(StatusOnibus.OPERANDO); em.persist(onibus);
            var rota = new Rota(); rota.setNome("Centro"); rota.setDescricao("Itinerário com descrição"); rota.setFaculdade(outra);
            rota.setPontosEmbarque(new ArrayList<>()); em.persist(rota);
            var ponto = new PontoEmbarque(); ponto.setDescricao("Praça Central"); ponto.setOrdemParada(1); ponto.setRota(rota); em.persist(ponto);
            rota.getPontosEmbarque().add(ponto);
            var grade = new GradeDiaria(); grade.setDiaSemana(DiaSemana.QUARTA); grade.setDescricao("Quarta-feira"); em.persist(grade);
            var viagem = new Viagem(); viagem.setGradeDiaria(grade); viagem.setData(LocalDate.of(1980, 1, 1));
            viagem.setRota(rota); viagem.setOnibus(onibus); viagem.setMotorista(motorista); viagem.setFaculdades(List.of(faculdade)); em.persist(viagem);
            viagemId = viagem.getId();
        });
        tokenAdmin = login("admin"); tokenAluno = login("aluno"); tokenOutro = login("outro"); tokenMotorista = login("motorista");
        webhook.verificar("subscribe", properties.getVerifyToken(), "desafio");
        when(meta.uploadPdf(any(), anyString())).thenAnswer(i -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return "media-ficticia";
        });
        when(meta.enviarProgramacao(anyString(), anyString(), anyString(), anyString(), anyString(), anyString())).thenAnswer(i -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return "wamid." + i.getArgument(5, String.class);
        });
    }

    @AfterEach
    void limpar() { SecurityContextHolder.clearContext(); }

    @Test
    void integracaoDesabilitadaNaoQuebraAPIAtualNemEnvia() throws Exception {
        properties.setEnabled(false);
        mvc.perform(get("/dashboard/aluno").header("Authorization", tokenAluno)).andExpect(status().isOk());
        mvc.perform(post("/whatsapp/minha-assinatura/ativacao").header("Authorization", tokenAluno)
                .contentType("application/json").content("{\"numero\":\"" + NUMERO + "\",\"aceite\":true}")).andExpect(status().isServiceUnavailable());
        fila.processarProxima(); verifyNoInteractions(meta);
    }

    @Test
    void autorizacaoObrigatoriaNumeroNormalizadoEHashSemCodigoEmClaro() {
        autenticar(tokenAluno);
        assertThatThrownBy(() -> assinaturas.solicitar(new AtivacaoRequest(NUMERO, false))).hasMessageContaining("autorizar");
        var retorno = assinaturas.solicitar(new AtivacaoRequest("+55 (11) 99999-0001", true));
        assertThat(retorno.assinatura().status()).isEqualTo("PENDENTE");
        assertThat(retorno.assinatura().numero()).isEqualTo(NUMERO);
        String codigo = codigo(retorno);
        tx.executeWithoutResult(s -> {
            var assinatura = repos.assinatura(alunoId);
            assertThat(assinatura.getCodigoHash()).isEqualTo(WhatsappSeguranca.hash(codigo)).doesNotContain(codigo);
            assertThat(assinatura.getCodigoExpiraEm()).isEqualTo(AGORA.plusSeconds(1800));
            assertThat(em.find(Pessoa.class, pessoaAlunoId).getTelefone()).isEqualTo("telefone-legado");
        });
    }

    @Test
    void assinaturaConfirmaSomentePeloNumeroCorretoECodigoDeUsoUnico() {
        autenticar(tokenAluno);
        var ativacao = assinaturas.solicitar(new AtivacaoRequest(NUMERO, true));
        comando(NUMERO_OUTRO, "ATIVAR " + codigo(ativacao), "msg-errada");
        assertThat(assinaturas.consultarMinha().status()).isEqualTo("PENDENTE");
        comando(NUMERO, "ATIVAR " + codigo(ativacao), "msg-certa");
        assertThat(assinaturas.consultarMinha().status()).isEqualTo("ATIVA");
        comando(NUMERO, "ATIVAR " + codigo(ativacao), "msg-certa");
        assertThat(assinaturas.consultarMinha().status()).isEqualTo("ATIVA");
        tx.executeWithoutResult(s -> assertThat(repos.assinatura(alunoId).getCodigoHash()).isNull());
    }

    @Test
    void expiracaoETrocaDeNumeroExigemNovaConfirmacao() {
        autenticar(tokenAluno);
        var antigo = assinaturas.solicitar(new AtivacaoRequest(NUMERO, true));
        when(dashboardClock.instant()).thenReturn(AGORA.plusSeconds(1800));
        comando(NUMERO, "ATIVAR " + codigo(antigo), "expirado");
        assertThat(assinaturas.consultarMinha().status()).isEqualTo("PENDENTE");
        when(dashboardClock.instant()).thenReturn(AGORA.plusSeconds(1801));
        var novo = assinaturas.solicitar(new AtivacaoRequest(NUMERO_OUTRO, true));
        comando(NUMERO, "ATIVAR " + codigo(antigo), "antigo");
        assertThat(assinaturas.consultarMinha().status()).isEqualTo("PENDENTE");
        comando(NUMERO_OUTRO, "ATIVAR " + codigo(novo), "novo");
        assertThat(assinaturas.consultarMinha().status()).isEqualTo("ATIVA");
    }

    @Test
    void limiteDeCincoAtivacoesPorHoraERetomada() {
        autenticar(tokenAluno);
        for (int i = 0; i < 5; i++) assinaturas.solicitar(new AtivacaoRequest(NUMERO, true));
        assertThatThrownBy(() -> assinaturas.solicitar(new AtivacaoRequest(NUMERO, true))).hasMessageContaining("cinco");
        when(dashboardClock.instant()).thenReturn(AGORA.plusSeconds(3600));
        assertThat(assinaturas.solicitar(new AtivacaoRequest(NUMERO, true)).linkAtivacao()).isNotBlank();
    }

    @Test
    void numeroAtivoNaoPodeSerCompartilhadoEDestinoDeTesteERestrito() {
        ativar(tokenAluno, NUMERO);
        autenticar(tokenOutro);
        assertThatThrownBy(() -> assinaturas.solicitar(new AtivacaoRequest(NUMERO, true))).hasMessageContaining("já possui");
        assertThatThrownBy(() -> assinaturas.solicitar(new AtivacaoRequest("+5511999991111", true))).hasMessageContaining("ambiente de teste");
    }

    @Test
    void cancelamentoNoPerfilENoWhatsappImpedeNovosEnviosEWebhookDuplicadoNaoReativa() {
        ativar(tokenAluno, NUMERO);
        comando(NUMERO, "SAIR", "cancelamento-1");
        autenticar(tokenAluno);
        assertThat(assinaturas.consultarMinha().status()).isEqualTo("CANCELADA");
        ativar(tokenAluno, NUMERO);
        comando(NUMERO, "SAIR", "cancelamento-1");
        assertThat(assinaturas.consultarMinha().status()).isEqualTo("ATIVA");
        assinaturas.cancelarMinha();
        assertThat(assinaturas.consultarMinha().status()).isEqualTo("CANCELADA");
    }

    @Test
    void webhookExigeAssinaturaValidaENaoAceitaOutroNumeroEmpresarial() throws Exception {
        autenticar(tokenAluno); var ativacao = assinaturas.solicitar(new AtivacaoRequest(NUMERO, true));
        byte[] corpo = corpo(Map.of("messages", List.of(mensagem(NUMERO, "ATIVAR " + codigo(ativacao), "msg"))));
        mvc.perform(post("/webhooks/whatsapp").contentType("application/json").content(corpo))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
        mvc.perform(post("/webhooks/whatsapp").header("X-Hub-Signature-256", "sha256=" + "0".repeat(64)).contentType("application/json").content(corpo))
                .andExpect(status().isUnauthorized());
        byte[] errado = new String(corpo, StandardCharsets.UTF_8).replace("\"456\"", "\"999\"").getBytes(StandardCharsets.UTF_8);
        webhook.receber(errado, assinar(errado));
        autenticar(tokenAluno);
        assertThat(assinaturas.consultarMinha().status()).isEqualTo("PENDENTE");
        mvc.perform(get("/webhooks/whatsapp").param("hub.mode", "subscribe").param("hub.verify_token", "errado").param("hub.challenge", "abc"))
                .andExpect(status().isForbidden());
    }

    @Test
    void programacaoMantemIsolamentoIgnoraDataAntigaEDeduplicaPontos() throws Exception {
        autenticar(tokenAluno);
        var aluno = tx.execute(s -> programacao.consultar(usuarioRepository.findById(alunoId).orElseThrow()));
        assertThat(aluno.viagens()).hasSize(1);
        assertThat(aluno.viagens().getFirst().rota().pontosEmbarque()).hasSize(1);
        var outro = tx.execute(s -> programacao.consultar(usuarioRepository.findById(outroAlunoId).orElseThrow()));
        assertThat(outro.viagens()).isEmpty(); // Faculdade principal da rota não define os destinos.
        var motorista = tx.execute(s -> programacao.consultar(usuarioRepository.findById(motoristaId).orElseThrow()));
        assertThat(motorista.viagens()).extracting(v -> v.id()).containsExactly(viagemId);
        mvc.perform(get("/whatsapp/minha-programacao/pdf").header("Authorization", tokenAluno))
                .andExpect(status().isOk()).andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(get("/whatsapp/minha-programacao/pdf").header("Authorization", tokenOutro)).andExpect(status().isNotFound());
        tx.executeWithoutResult(s -> assertThat(em.find(Viagem.class, viagemId).getData()).isEqualTo(LocalDate.of(1980, 1, 1)));
    }

    @Test
    void webhookLimitaTamanhoAntesDeCarregarPayloadCompleto() throws Exception {
        mvc.perform(post("/webhooks/whatsapp").contentType("application/json").content(new byte[2_000_001]))
                .andExpect(status().isPayloadTooLarge()).andExpect(jsonPath("$.status").value(413));
    }

    @Test
    void contaPendentePodeReceberEAlunoSemProgramacaoFicaSuprimido() {
        ativar(tokenAluno, NUMERO); ativar(tokenOutro, NUMERO_OUTRO); ativar(tokenMotorista, NUMERO_MOTORISTA);
        autenticar(tokenAdmin);
        var lote = lotes.solicitarManual(request(Publico.TODOS));
        assertThat(lote.totais().get(StatusEntrega.PENDENTE)).isEqualTo(2);
        assertThat(lote.totais().get(StatusEntrega.SUPRIMIDA)).isEqualTo(1);
        assertThat(lote.entregas()).anySatisfy(e -> assertThat(e.erro()).contains("SEM_PROGRAMACAO"));
        tx.executeWithoutResult(s -> assertThat(em.find(Aluno.class, pessoaAlunoId).getStatusMatricula()).isEqualTo(StatusMatricula.PENDENTE));
    }

    @Test
    void requestIdRepetidoNaoCriaLoteNovoEOMesmoDiaNaoDuplica() {
        ativar(tokenAluno, NUMERO); autenticar(tokenAdmin);
        var request = request(Publico.ALUNOS);
        var primeiro = lotes.solicitarManual(request);
        assertThat(lotes.solicitarManual(request).id()).isEqualTo(primeiro.id());
        assertThat(lotes.solicitarManual(request(Publico.ALUNOS)).ignoradosDuplicidade()).isEqualTo(1);
        assertThatThrownBy(() -> lotes.solicitarManual(new EnvioRequest(request.requestId(), Publico.TODOS, false, null)))
                .hasMessageContaining("parâmetros diferentes");
    }

    @Test
    void reenvioExigeMotivoEGeraNovaRevisaoSemAlterarSnapshotAnterior() {
        ativar(tokenAluno, NUMERO); autenticar(tokenAdmin);
        var primeiro = lotes.solicitarManual(request(Publico.ALUNOS));
        assertThatThrownBy(() -> lotes.solicitarManual(new EnvioRequest(UUID.randomUUID(), Publico.ALUNOS, true, ""))).hasMessageContaining("motivo");
        tx.executeWithoutResult(s -> em.find(Viagem.class, viagemId).getOnibus().setPlaca("NOVA123"));
        var segundo = lotes.solicitarManual(new EnvioRequest(UUID.randomUUID(), Publico.ALUNOS, true, "Correção da placa"));
        assertThat(segundo.entregas().getFirst().revisao()).isEqualTo(1);
        tx.executeWithoutResult(s -> {
            assertThat(repos.entregas(primeiro.id()).getFirst().getProgramacaoJson()).contains("ABC1234").doesNotContain("NOVA123");
            assertThat(repos.entregas(segundo.id()).getFirst().getProgramacaoJson()).contains("NOVA123");
        });
    }

    @Test
    void filaEnviaForaDaTransacaoRespeitaRitmoEConfereCancelamento() {
        ativar(tokenAluno, NUMERO); ativar(tokenMotorista, NUMERO_MOTORISTA); autenticar(tokenAdmin);
        var lote = lotes.solicitarManual(request(Publico.TODOS));
        fila.processarProxima(); fila.processarProxima();
        verify(meta, times(1)).enviarProgramacao(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
        when(dashboardClock.instant()).thenReturn(AGORA.plusSeconds(1));
        fila.processarProxima();
        assertThat(lotes.detalhar(lote.id()).totais().get(StatusEntrega.ACEITA)).isEqualTo(2);
    }

    @Test
    void cancelamentoDuranteUploadEvitaEnviarMensagem() {
        ativar(tokenAluno, NUMERO); autenticar(tokenAdmin);
        var lote = lotes.solicitarManual(request(Publico.ALUNOS));
        when(meta.uploadPdf(any(), anyString())).thenAnswer(i -> {
            tx.executeWithoutResult(s -> repos.assinatura(alunoId).setStatus(StatusAssinatura.CANCELADA));
            return "media-ficticia";
        });
        fila.processarProxima();
        assertThat(lotes.detalhar(lote.id()).totais().get(StatusEntrega.SUPRIMIDA)).isEqualTo(1);
        verify(meta, never()).enviarProgramacao(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void contaInativaOuExcluidaNaoRecebeEHistoricoSobrevive() {
        ativar(tokenAluno, NUMERO); autenticar(tokenAdmin);
        var lote = lotes.solicitarManual(request(Publico.ALUNOS));
        tx.executeWithoutResult(s -> em.remove(em.find(Usuario.class, alunoId)));
        fila.processarProxima();
        assertThat(lotes.detalhar(lote.id()).totais().get(StatusEntrega.SUPRIMIDA)).isEqualTo(1);
        tx.executeWithoutResult(s -> {
            assertThat(em.find(Pessoa.class, pessoaAlunoId)).isNotNull();
            assertThat(repos.assinatura(alunoId)).isNull();
            assertThat(repos.entregas(lote.id()).getFirst().getUsuario()).isNull();
        });
    }

    @Test
    void resultadoIncertoNuncaRepeteAutomaticamente() {
        ativar(tokenAluno, NUMERO); autenticar(tokenAdmin);
        var lote = lotes.solicitarManual(request(Publico.ALUNOS));
        when(meta.enviarProgramacao(anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new MetaWhatsappException("META_CONEXAO", true, false));
        fila.processarProxima();
        when(dashboardClock.instant()).thenReturn(AGORA.plusSeconds(600));
        fila.processarProxima();
        assertThat(lotes.detalhar(lote.id()).totais().get(StatusEntrega.INCERTA)).isEqualTo(1);
        verify(meta, times(1)).enviarProgramacao(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void contaDesativadaDepoisDoLoteNaoRecebe() {
        ativar(tokenAluno, NUMERO); autenticar(tokenAdmin);
        var lote = lotes.solicitarManual(request(Publico.ALUNOS));
        tx.executeWithoutResult(s -> em.find(Usuario.class, alunoId).setAtivo(false));
        fila.processarProxima();
        assertThat(lotes.detalhar(lote.id()).totais().get(StatusEntrega.SUPRIMIDA)).isEqualTo(1);
        verify(meta, never()).enviarProgramacao(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void cliquesEWorkersConcorrentesNaoDuplicamMensagens() throws Exception {
        ativar(tokenAluno, NUMERO);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var chamadas = List.<java.util.concurrent.Callable<UUID>>of(
                () -> { autenticar(tokenAdmin); try { return lotes.solicitarManual(request(Publico.ALUNOS)).id(); } finally { SecurityContextHolder.clearContext(); } },
                () -> { autenticar(tokenAdmin); try { return lotes.solicitarManual(request(Publico.ALUNOS)).id(); } finally { SecurityContextHolder.clearContext(); } });
            var resultados = executor.invokeAll(chamadas);
            for (var resultado : resultados) assertThat(resultado.get()).isNotNull();
            var workers = executor.invokeAll(List.<java.util.concurrent.Callable<Void>>of(
                () -> { fila.processarProxima(); return null; }, () -> { fila.processarProxima(); return null; }));
            for (var worker : workers) worker.get();
        }
        verify(meta, times(1)).enviarProgramacao(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void falhaComprovadaRepeteAposUmCincoEQuinzeMinutosNoMaximo() {
        ativar(tokenAluno, NUMERO); autenticar(tokenAdmin);
        var lote = lotes.solicitarManual(request(Publico.ALUNOS));
        when(meta.uploadPdf(any(), anyString())).thenThrow(new MetaWhatsappException("META_HTTP_429", false, true));
        int[] segundos = {0, 60, 360, 1260};
        for (int segundo : segundos) { when(dashboardClock.instant()).thenReturn(AGORA.plusSeconds(segundo)); fila.processarProxima(); }
        var entrega = lotes.detalhar(lote.id()).entregas().getFirst();
        assertThat(entrega.status()).isEqualTo(StatusEntrega.FALHA); assertThat(entrega.tentativas()).isEqualTo(4);
    }

    @Test
    void recuperacaoAte18EManualImpedeAutomaticoDuplicado() {
        ativar(tokenAluno, NUMERO); autenticar(tokenAdmin);
        when(dashboardClock.instant()).thenReturn(AGORA.minusSeconds(3600));
        lotes.solicitarManual(request(Publico.ALUNOS));
        administracao.atualizar(new ConfiguracaoRequest(true));
        rotina.recuperarHoje();
        assertThat(lotes.historico(0, 20).total()).isEqualTo(1);
        when(dashboardClock.instant()).thenReturn(AGORA.plusSeconds(7200));
        SecurityContextHolder.clearContext(); rotina.recuperarHoje(); rotina.recuperarHoje();
        autenticar(tokenAdmin);
        assertThat(lotes.historico(0, 20).total()).isEqualTo(2);
        assertThat(lotes.detalhar(lotes.historico(0, 20).itens().getFirst().id()).ignoradosDuplicidade()).isEqualTo(1);
        when(dashboardClock.instant()).thenReturn(AGORA.plusSeconds(10800));
        rotina.recuperarHoje();
        assertThat(lotes.historico(0, 20).total()).isEqualTo(2);
    }

    @Test
    void automaticoPendenteExpiraAs18EManualNaMeiaNoite() {
        ativar(tokenAluno, NUMERO); autenticar(tokenAdmin); administracao.atualizar(new ConfiguracaoRequest(true));
        SecurityContextHolder.clearContext(); rotina.recuperarHoje();
        when(dashboardClock.instant()).thenReturn(AGORA.plusSeconds(10800));
        fila.processarProxima(); autenticar(tokenAdmin);
        var id = lotes.historico(0, 20).itens().getFirst().id();
        assertThat(lotes.detalhar(id).totais().get(StatusEntrega.EXPIRADA)).isEqualTo(1);
        var manual = lotes.solicitarManual(new EnvioRequest(UUID.randomUUID(), Publico.ALUNOS, true, "Envio manual após o horário"));
        when(dashboardClock.instant()).thenReturn(Instant.parse("2026-10-15T03:00:00Z"));
        fila.processarProxima();
        assertThat(lotes.detalhar(manual.id()).totais().get(StatusEntrega.EXPIRADA)).isEqualTo(1);
    }

    @Test
    void reservaInterrompidaViraIncertaSemRepetir() {
        ativar(tokenAluno, NUMERO); autenticar(tokenAdmin);
        var lote = lotes.solicitarManual(request(Publico.ALUNOS));
        tx.executeWithoutResult(s -> {
            var entrega = repos.entregas(lote.id()).getFirst();
            entrega.setStatus(StatusEntrega.PROCESSANDO); entrega.setProcessamentoEm(AGORA.minusSeconds(601)); entrega.setReserva(UUID.randomUUID());
        });
        fila.processarProxima();
        assertThat(lotes.detalhar(lote.id()).totais().get(StatusEntrega.INCERTA)).isEqualTo(1);
        verify(meta, never()).enviarProgramacao(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void eventosDeEntregaForaDeOrdemNaoRegridemECallbackAntesDoRetornoFunciona() {
        ativar(tokenAluno, NUMERO); autenticar(tokenAdmin);
        var lote = lotes.solicitarManual(request(Publico.ALUNOS));
        when(meta.enviarProgramacao(anyString(), anyString(), anyString(), anyString(), anyString(), anyString())).thenAnswer(i -> {
            String id = i.getArgument(5); String mensagem = "wamid." + id;
            estado(mensagem, id, "read"); return mensagem;
        });
        fila.processarProxima();
        var entrega = lotes.detalhar(lote.id()).entregas().getFirst();
        assertThat(entrega.status()).isEqualTo(StatusEntrega.LIDA);
        estado("wamid." + entrega.id(), entrega.id().toString(), "sent");
        estado("wamid." + entrega.id(), entrega.id().toString(), "failed");
        assertThat(lotes.detalhar(lote.id()).entregas().getFirst().status()).isEqualTo(StatusEntrega.LIDA);
    }

    @Test
    void matrizDePermissoesEContratosSemCredenciais() throws Exception {
        for (String caminho : List.of("/whatsapp/minha-assinatura", "/integracoes/whatsapp/configuracao", "/integracoes/whatsapp/envios")) {
            mvc.perform(get(caminho)).andExpect(status().isUnauthorized());
        }
        mvc.perform(get("/whatsapp/minha-assinatura").header("Authorization", tokenAdmin)).andExpect(status().isForbidden());
        mvc.perform(get("/integracoes/whatsapp/configuracao").header("Authorization", tokenAluno)).andExpect(status().isForbidden());
        mvc.perform(get("/integracoes/whatsapp/configuracao").header("Authorization", tokenMotorista)).andExpect(status().isForbidden());
        mvc.perform(get("/integracoes/whatsapp/configuracao").header("Authorization", tokenAdmin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").doesNotExist()).andExpect(jsonPath("$.appSecret").doesNotExist());
        mvc.perform(get("/whatsapp/minha-assinatura").header("Authorization", tokenAluno)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NAO_CADASTRADA")).andExpect(jsonPath("$.codigoHash").doesNotExist());
        mvc.perform(get("/integracoes/whatsapp/envios").param("tamanho", "101").header("Authorization", tokenAdmin)).andExpect(status().isBadRequest());
        autenticar(tokenAluno);
        assertThatThrownBy(() -> lotes.historico(0, 20)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> administracao.atualizar(new ConfiguracaoRequest(true))).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void semProgramacaoNoFimDeSemanaNaoEnviaEEstadoIndisponivelEAdvertido() {
        ativar(tokenAluno, NUMERO);
        tx.executeWithoutResult(s -> em.find(Viagem.class, viagemId).getOnibus().setStatusOnibus(StatusOnibus.MANUTENCAO));
        autenticar(tokenAdmin);
        assertThat(lotes.previa(alunoId).programacao().viagens().getFirst().avisos()).isNotEmpty();
        when(dashboardClock.instant()).thenReturn(Instant.parse("2026-10-17T18:00:00Z"));
        var lote = lotes.solicitarManual(request(Publico.ALUNOS));
        assertThat(lote.totais().get(StatusEntrega.SUPRIMIDA)).isEqualTo(1);
        fila.processarProxima(); verify(meta, never()).uploadPdf(any(), anyString());
    }

    protected void autenticar(String token) {
        SecurityContextHolder.getContext().setAuthentication(converter.convert(decoder.decode(token.substring(7))));
    }
    private String login(String prefixo) { return "Bearer " + auth.login(new LoginRequest(prefixo + "@whatsapp.test", "SenhaTeste123")).accessToken(); }
    private EnvioRequest request(Publico publico) { return new EnvioRequest(UUID.randomUUID(), publico, false, null); }
    private String codigo(AtivacaoResponse ativacao) {
        String query = URI.create(ativacao.linkAtivacao()).getRawQuery();
        return URLDecoder.decode(query.substring(5), StandardCharsets.UTF_8).substring(7);
    }
    private void ativar(String token, String numero) {
        autenticar(token); var ativacao = assinaturas.solicitar(new AtivacaoRequest(numero, true));
        comando(numero, "ATIVAR " + codigo(ativacao), UUID.randomUUID().toString());
    }
    private Map<String, Object> mensagem(String numero, String texto, String id) {
        return Map.of("id", id, "from", numero.substring(1), "type", "text", "timestamp", String.valueOf(dashboardClock.instant().getEpochSecond()), "text", Map.of("body", texto));
    }
    private void comando(String numero, String texto, String id) {
        byte[] dados = corpo(Map.of("messages", List.of(mensagem(numero, texto, id))));
        webhook.receber(dados, assinar(dados));
    }
    private void estado(String mensagem, String correlacao, String status) {
        byte[] dados = corpo(Map.of("statuses", List.of(Map.of("id", mensagem, "biz_opaque_callback_data", correlacao, "status", status, "recipient_id", NUMERO.substring(1)))));
        webhook.receber(dados, assinar(dados));
    }
    private byte[] corpo(Map<String, Object> valores) {
        Map<String, Object> value = new HashMap<>(valores); value.put("metadata", Map.of("phone_number_id", "456"));
        return json.writeValueAsBytes(Map.of("object", "whatsapp_business_account", "entry", List.of(Map.of("id", "123", "changes", List.of(Map.of("field", "messages", "value", value))))));
    }
    private String assinar(byte[] corpo) {
        try {
            Mac hmac = Mac.getInstance("HmacSHA256"); hmac.init(new SecretKeySpec(properties.getAppSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return "sha256=" + HexFormat.of().formatHex(hmac.doFinal(corpo));
        } catch (Exception exception) { throw new IllegalStateException(exception); }
    }
}
