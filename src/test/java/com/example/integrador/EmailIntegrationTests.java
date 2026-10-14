package com.example.integrador;

import com.example.integrador.Config.DashboardClockConfig;
import com.example.integrador.DTO.LoginRequest;
import com.example.integrador.Email.*;
import com.example.integrador.Enum.*;
import com.example.integrador.Exception.*;
import com.example.integrador.Model.*;
import com.example.integrador.Repository.UsuarioRepository;
import com.example.integrador.Service.AuthService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:emailtest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "app.email.enabled=true", "app.email.production=true", "app.email.from=transporte@example.test",
        "app.email.smtp-host=smtp.example.test", "app.email.schedulers-enabled=false"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class EmailIntegrationTests {
    private static final Instant INICIO = Instant.parse("2026-10-12T18:00:00Z"); // Segunda, 15h SP.
    @Autowired MockMvc mvc;
    @Autowired EmailLoteService lotes;
    @Autowired EmailAdministracaoService administracao;
    @Autowired EmailFilaService fila;
    @Autowired EmailRotinaService rotina;
    @Autowired EmailRepository repository;
    @Autowired EmailProperties properties;
    @Autowired UsuarioRepository usuarios;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager manager;
    @Autowired PasswordEncoder encoder;
    @Autowired AuthService auth;
    @Autowired JwtDecoder decoder;
    @Autowired JwtAuthenticationConverter converter;
    @MockitoBean Clock dashboardClock;
    @MockitoBean EmailGateway gateway;
    private TransactionTemplate tx;
    private Usuario admin, aluno, motorista;
    private Faculdade faculdade, outra;
    private Onibus onibus;
    private Instant agora;

    @BeforeEach
    void preparar() {
        agora = INICIO;
        when(dashboardClock.instant()).thenAnswer(i -> agora);
        when(dashboardClock.getZone()).thenReturn(DashboardClockConfig.ZONA);
        reset(gateway);
        SecurityContextHolder.clearContext();
        tx = new TransactionTemplate(manager);
        properties.setEnabled(true);
        properties.setProduction(true);
        properties.setSchedulersEnabled(false);
        properties.getAllowedRecipients().clear();
        tx.executeWithoutResult(status -> {
            em.createQuery("delete EmailEntrega").executeUpdate();
            em.createQuery("delete EmailLote").executeUpdate();
            em.createNativeQuery("delete from viagem_faculdade").executeUpdate();
            em.createQuery("delete Viagem").executeUpdate();
            em.createNativeQuery("delete from rota_pontos_embarque").executeUpdate();
            em.createQuery("delete PontoEmbarque").executeUpdate();
            em.createQuery("delete Rota").executeUpdate();
            em.createQuery("delete GradeDiaria").executeUpdate();
            em.createQuery("delete Usuario").executeUpdate();
            em.createQuery("delete Pessoa").executeUpdate();
            em.createQuery("delete Faculdade").executeUpdate();
            em.createQuery("delete Onibus").executeUpdate();
            var config = repository.bloquearConfiguracao();
            config.setAutomatico(false);
            config.setUltimaReserva(null);
            faculdade = faculdade("Faculdade São José");
            outra = faculdade("Universidade Águas Claras");
            admin = usuario(TipoPessoa.ADMIN, "admin@example.test", true);
            aluno = usuario(TipoPessoa.ALUNO, "aluno@example.test", true);
            motorista = usuario(TipoPessoa.MOTORISTA, "motorista@example.test", true);
            onibus = new Onibus();
            onibus.setNumeroIdentificacao("07");
            onibus.setPlaca("ABC1D23");
            onibus.setCapacidade(40);
            onibus.setStatusOnibus(StatusOnibus.OPERANDO);
            onibus.setFotoUrl("foto-sensivel");
            em.persist(onibus);
            viagem(DiaSemana.SEGUNDA, LocalDate.of(2000, 1, 1), faculdade, outra);
        });
        autenticar(admin);
    }
    @AfterEach void limparContexto() { SecurityContextHolder.clearContext(); }

    @Test
    void previaGeralUsaTodasGradesEDestinosIgnorandoDataLegada() {
        tx.executeWithoutResult(s -> {
            viagem(DiaSemana.SEGUNDA, LocalDate.of(2090, 1, 1), outra);
            viagem(DiaSemana.TERCA, LocalDate.of(2026, 10, 12), faculdade);
            viagem(null, LocalDate.of(2026, 10, 12), faculdade);
        });
        var previa = lotes.previa();
        assertThat(previa.viagens()).hasSize(2);
        assertThat(previa.destinatariosElegiveis()).isEqualTo(2);
        assertThat(previa.mensagem().texto()).contains("Faculdade São José", "Universidade Águas Claras", "Ônibus: 07", "Motorista: Motorista");
        assertThat(previa.mensagem().html()).doesNotContain("foto-sensivel", "cpf", "CNH", "salario", "aluno@example.test");
        assertThat(previa.viagens()).allSatisfy(v -> assertThat(v.dataOcorrencia()).isEqualTo(LocalDate.of(2026, 10, 12)));
    }

    @Test
    void todosRecebemMesmoConteudoSemAdminNemContaInativa() {
        tx.executeWithoutResult(s -> usuario(TipoPessoa.ALUNO, "inativo@example.test", false));
        var lote = enviar(false, null);
        assertThat(lote.entregas()).extracting(EmailDto.EntregaResponse::email)
                .containsExactly("aluno@example.test", "motorista@example.test");
        List<EmailDto.Conteudo> mensagens = new ArrayList<>();
        doAnswer(inv -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            mensagens.add(inv.getArgument(2));
            return null;
        }).when(gateway).enviar(any(), anyString(), any());
        fila.processarUma(); avancar(1); fila.processarUma();
        assertThat(mensagens).hasSize(2).allMatch(lote.mensagem()::equals);
        assertThat(lotes.detalhe(lote.id()).entregas()).allSatisfy(e -> assertThat(e.estado()).isEqualTo(EmailDto.Estado.ACEITA_SMTP));
    }

    @Test
    void idempotenciaEConflitoDeParametros() {
        UUID id = UUID.randomUUID();
        var request = new EmailDto.EnvioRequest(id, false, null);
        assertThat(lotes.solicitar(request).id()).isEqualTo(lotes.solicitar(request).id());
        assertThatThrownBy(() -> lotes.solicitar(new EmailDto.EnvioRequest(id, true, "Correção")))
                .isInstanceOf(ConflitoException.class);
        assertThat(lotes.historico(0, 20).totalElementos()).isEqualTo(1);
    }

    @Test
    void manualNaoRepeteNoAutomaticoEReenvioExigeMotivo() {
        enviar(false, null);
        administracao.atualizar(true);
        lotes.solicitarAutomatico();
        var historico = lotes.historico(0, 20);
        assertThat(historico.itens()).hasSize(2);
        assertThat(historico.itens().stream().filter(l -> l.origem() == EmailDto.Origem.AUTOMATICA).findFirst().orElseThrow().ignorados()).isEqualTo(2);
        assertThatThrownBy(() -> enviar(true, " ")).isInstanceOf(RegraNegocioException.class);
        assertThat(enviar(true, "Ônibus corrigido").entregas())
                .allSatisfy(e -> assertThat(e.revisao()).isEqualTo(1));
    }

    @Test
    void conteudoCongeladoNaoMudaQuandoCadastroMuda() {
        var lote = enviar(false, null);
        tx.executeWithoutResult(s -> em.find(Onibus.class, onibus.getId()).setNumeroIdentificacao("99"));
        assertThat(lotes.previa().mensagem().texto()).contains("Ônibus: 99");
        assertThat(lotes.detalhe(lote.id()).mensagem().texto()).contains("Ônibus: 07").doesNotContain("Ônibus: 99");
    }

    @Test
    void contaInativaExcluidaOuEmailAlteradoSuprimeFila() {
        var lote = enviar(false, null);
        tx.executeWithoutResult(s -> {
            em.find(Usuario.class, aluno.getId()).setEmail("alterado@example.test");
            em.find(Usuario.class, motorista.getId()).setAtivo(false);
        });
        fila.processarUma(); avancar(1); fila.processarUma();
        verifyNoInteractions(gateway);
        assertThat(lotes.detalhe(lote.id()).entregas()).allSatisfy(e -> assertThat(e.estado()).isEqualTo(EmailDto.Estado.SUPRIMIDA));
    }

    @Test
    void excluirUsuarioPreservaHistoricoEPessoa() {
        var lote = enviar(false, null);
        Long pessoaId = aluno.getPessoa().getId();
        tx.executeWithoutResult(s -> em.remove(em.find(Usuario.class, aluno.getId())));
        fila.processarUma(); avancar(1); fila.processarUma();
        assertThat(lotes.detalhe(lote.id()).entregas()).hasSize(2);
        Pessoa preservada = tx.execute(s -> em.find(Pessoa.class, pessoaId));
        assertThat(preservada).isNotNull();
        verify(gateway, times(1)).enviar(any(), eq("motorista@example.test"), any());
    }

    @Test
    void modoTesteSoEnviaParaListaPermitida() {
        properties.setProduction(false);
        properties.getAllowedRecipients().add("ALUNO@example.test");
        var previa = lotes.previa();
        assertThat(previa.destinatariosElegiveis()).isEqualTo(1);
        assertThat(previa.destinatariosIgnorados()).isEqualTo(1);
        var lote = enviar(false, null);
        fila.processarUma();
        assertThat(lote.entregas()).hasSize(1);
        verify(gateway).enviar(any(), eq("aluno@example.test"), any());
    }

    @Test
    void semProgramacaoNaoCriaLoteEWeekendNaoUsaDataAntiga() {
        agora = Instant.parse("2026-10-17T18:00:00Z");
        assertThat(lotes.previa().viagens()).isEmpty();
        assertThatThrownBy(() -> enviar(false, null)).isInstanceOf(ConflitoException.class);
        administracao.atualizar(true);
        lotes.solicitarAutomatico();
        assertThat(lotes.historico(0, 20).totalElementos()).isZero();
    }

    @Test
    void configuracaoDesativadaNaoEnviaENaoAtivaAutomatico() {
        properties.setEnabled(false);
        assertThat(administracao.consultar().disponivel()).isFalse();
        assertThatThrownBy(() -> enviar(false, null)).isInstanceOf(IntegracaoEmailIndisponivelException.class);
        assertThatThrownBy(() -> administracao.atualizar(true)).isInstanceOf(IntegracaoEmailIndisponivelException.class);
        assertThat(administracao.atualizar(false).automatico()).isFalse();
        fila.processarUma();
        verifyNoInteractions(gateway);
    }

    @Test
    void recuperacaoRespeitaHorarioENaoDuplica() {
        administracao.atualizar(true);
        agora = Instant.parse("2026-10-12T17:59:00Z");
        lotes.solicitarAutomatico();
        assertThat(lotes.historico(0, 20).totalElementos()).isZero();
        agora = Instant.parse("2026-10-12T20:59:00Z");
        lotes.solicitarAutomatico(); lotes.solicitarAutomatico();
        assertThat(lotes.historico(0, 20).totalElementos()).isEqualTo(1);
        agora = Instant.parse("2026-10-13T21:00:00Z");
        lotes.solicitarAutomatico();
        assertThat(lotes.historico(0, 20).totalElementos()).isEqualTo(1);
    }

    @Test
    void automaticoPendenteExpiraAs18EManualNaMeiaNoite() {
        administracao.atualizar(true);
        lotes.solicitarAutomatico();
        var automatico = lotes.historico(0, 20).itens().getFirst();
        agora = Instant.parse("2026-10-12T21:00:00Z");
        fila.processarUma();
        assertThat(lotes.detalhe(automatico.id()).entregas()).allSatisfy(e -> assertThat(e.estado()).isEqualTo(EmailDto.Estado.EXPIRADA));
        var manual = enviar(true, "Correção à noite");
        agora = Instant.parse("2026-10-13T03:00:00Z");
        fila.processarUma();
        assertThat(lotes.detalhe(manual.id()).entregas()).allSatisfy(e -> assertThat(e.estado()).isEqualTo(EmailDto.Estado.EXPIRADA));
        verifyNoInteractions(gateway);
    }

    @Test
    void falhaAmbiguaNaoTemRepeticaoAutomatica() {
        var lote = enviar(false, null);
        doThrow(new EmailEnvioException("RESULTADO_SMTP_INCERTO", true, false)).when(gateway).enviar(any(), anyString(), any());
        fila.processarUma(); avancar(1); fila.processarUma(); avancar(3600); fila.processarUma();
        verify(gateway, times(2)).enviar(any(), anyString(), any());
        assertThat(lotes.detalhe(lote.id()).entregas()).allSatisfy(e -> assertThat(e.estado()).isEqualTo(EmailDto.Estado.INCERTA));
    }

    @Test
    void falhaDeConexaoAnteriorAoEnvioPermiteAteQuatroTentativas() {
        properties.setProduction(false);
        properties.getAllowedRecipients().add("aluno@example.test");
        var lote = enviar(false, null);
        doThrow(new EmailEnvioException("CONEXAO_SMTP", false, true)).when(gateway).enviar(any(), anyString(), any());
        fila.processarUma(); avancar(59); fila.processarUma();
        verify(gateway, times(1)).enviar(any(), anyString(), any());
        avancar(1); fila.processarUma(); avancar(300); fila.processarUma(); avancar(900); fila.processarUma();
        var entrega = lotes.detalhe(lote.id()).entregas().getFirst();
        assertThat(entrega.tentativas()).isEqualTo(4);
        assertThat(entrega.estado()).isEqualTo(EmailDto.Estado.FALHA);
    }

    @Test
    void reservaInterrompidaViraIncertaSemRepetir() {
        var lote = enviar(false, null);
        UUID entregaId = lote.entregas().getFirst().id();
        tx.executeWithoutResult(s -> {
            var entrega = repository.entrega(entregaId);
            entrega.setEstado(EmailDto.Estado.PROCESSANDO);
            entrega.setReservadoEm(agora.minusSeconds(601));
        });
        fila.processarUma();
        assertThat(lotes.detalhe(lote.id()).entregas().stream().filter(e -> e.id().equals(entregaId)).findFirst().orElseThrow().estado())
                .isEqualTo(EmailDto.Estado.INCERTA);
    }

    @Test
    void ritmoGlobalUmaMensagemPorSegundo() {
        enviar(false, null);
        fila.processarUma(); fila.processarUma();
        verify(gateway, times(1)).enviar(any(), anyString(), any());
        avancar(1); fila.processarUma();
        verify(gateway, times(2)).enviar(any(), anyString(), any());
    }

    @Test
    void criacaoConcorrenteDoAutomaticoNaoDuplica() throws Exception {
        administracao.atualizar(true);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> lotes.solicitarAutomatico());
            var b = executor.submit(() -> lotes.solicitarAutomatico());
            a.get(10, TimeUnit.SECONDS); b.get(10, TimeUnit.SECONDS);
        }
        assertThat(lotes.historico(0, 20).totalElementos()).isEqualTo(1);
        assertThat(lotes.historico(0, 20).itens().getFirst().totais().get(EmailDto.Estado.PENDENTE)).isEqualTo(2);
    }

    @Test
    void reservaConcorrenteNaoEnviaDuasVezes() throws Exception {
        enviar(false, null);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> fila.processarUma());
            var b = executor.submit(() -> fila.processarUma());
            a.get(10, TimeUnit.SECONDS); b.get(10, TimeUnit.SECONDS);
        }
        verify(gateway, times(1)).enviar(any(), anyString(), any());
    }

    @Test
    void estadosDoOnibusSaoAvisosENaoCancelamViagem() {
        tx.executeWithoutResult(s -> em.find(Onibus.class, onibus.getId()).setStatusOnibus(StatusOnibus.MANUTENCAO));
        var previa = lotes.previa();
        assertThat(previa.viagens()).hasSize(1);
        assertThat(previa.mensagem().texto()).contains("MANUTENCAO", "ATENÇÃO", "Ônibus indisponível");
    }

    @Test
    void permissoesHttpEDosServicosSemSegredos() throws Exception {
        String[] urls = {"/integracoes/email/configuracao", "/integracoes/email/previa", "/integracoes/email/envios"};
        SecurityContextHolder.clearContext();
        for (String url : urls) {
            mvc.perform(get(url)).andExpect(status().isUnauthorized());
            mvc.perform(get(url).header("Authorization", token(aluno))).andExpect(status().isForbidden());
            mvc.perform(get(url).header("Authorization", token(motorista))).andExpect(status().isForbidden());
            mvc.perform(get(url).header("Authorization", token(admin))).andExpect(status().isOk());
        }
        autenticar(aluno);
        assertThatThrownBy(() -> lotes.previa()).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> administracao.consultar()).isInstanceOf(AccessDeniedException.class);
        mvc.perform(get("/integracoes/email/configuracao").header("Authorization", token(admin)))
                .andExpect(jsonPath("$.smtpPassword").doesNotExist())
                .andExpect(jsonPath("$.smtpUsername").doesNotExist());
    }

    @Test
    void contratosValidacaoHistoricoE202() throws Exception {
        mvc.perform(post("/integracoes/email/envios").header("Authorization", token(admin))
                .contentType("application/json").content("{}")).andExpect(status().isBadRequest());
        mvc.perform(put("/integracoes/email/configuracao").header("Authorization", token(admin))
                .contentType("application/json").content("{}")).andExpect(status().isBadRequest());
        mvc.perform(get("/integracoes/email/envios").param("tamanho", "101").header("Authorization", token(admin)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/integracoes/email/envios/" + UUID.randomUUID()).header("Authorization", token(admin)))
                .andExpect(status().isNotFound());
        mvc.perform(post("/integracoes/email/envios").header("Authorization", token(admin))
                .contentType("application/json").content("{\"requestId\":\"" + UUID.randomUUID() + "\",\"reenviar\":false}"))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.entregas.length()").value(2))
                .andExpect(jsonPath("$.mensagem.texto").exists());
    }

    @Test
    void rotinaNaoExecutaComSchedulerDesativado() {
        administracao.atualizar(true);
        rotina.enviarAs15(); rotina.recuperar(); rotina.processarFila();
        assertThat(lotes.historico(0, 20).totalElementos()).isZero();
        verifyNoInteractions(gateway);
    }

    @Test
    void pontosSaoDeduplicadosEOrdenadosPelosDoisVinculos() {
        tx.executeWithoutResult(s -> {
            Rota rota = em.createQuery("from Rota", Rota.class).getResultList().getFirst();
            PontoEmbarque a = new PontoEmbarque(); a.setDescricao("Segundo"); a.setOrdemParada(2); a.setRota(rota); em.persist(a);
            PontoEmbarque b = new PontoEmbarque(); b.setDescricao("Primeiro"); b.setOrdemParada(1); em.persist(b);
            rota.setPontosEmbarque(new ArrayList<>(List.of(a, b)));
        });
        assertThat(lotes.previa().viagens().getFirst().rota().pontosEmbarque())
                .extracting(p -> p.descricao()).containsExactly("Primeiro", "Segundo");
    }

    @Test
    void htmlEscapaCamposCadastradosETextoMantemAcentos() {
        tx.executeWithoutResult(s -> em.find(Faculdade.class, faculdade.getId()).setNome("<script>alert(1)</script> São José"));
        var mensagem = lotes.previa().mensagem();
        assertThat(mensagem.html()).contains("&lt;script&gt;", "São José").doesNotContain("<script>");
        assertThat(mensagem.texto()).contains("São José");
    }

    @Test
    void dataReferenciaUsaSaoPauloMesmoDepoisDeMeiaNoiteUtc() {
        agora = Instant.parse("2026-10-13T02:30:00Z");
        assertThat(lotes.previa().dataReferencia()).isEqualTo(LocalDate.of(2026, 10, 12));
        assertThat(lotes.previa().viagens()).hasSize(1);
    }

    @Test
    void semDestinatariosNaoEnfileiraLoteManual() {
        properties.setProduction(false);
        properties.getAllowedRecipients().add("nao-cadastrado@example.test");
        assertThat(lotes.previa().destinatariosElegiveis()).isZero();
        assertThatThrownBy(() -> enviar(false, null)).isInstanceOf(ConflitoException.class);
    }

    private EmailDto.LoteResponse enviar(boolean reenviar, String motivo) {
        return lotes.solicitar(new EmailDto.EnvioRequest(UUID.randomUUID(), reenviar, motivo));
    }
    private void avancar(long segundos) { agora = agora.plusSeconds(segundos); }
    private Faculdade faculdade(String nome) {
        Faculdade f = new Faculdade(); f.setNome(nome); em.persist(f); return f;
    }
    private Usuario usuario(TipoPessoa tipo, String email, boolean ativo) {
        Pessoa pessoa = switch (tipo) {
            case ALUNO -> new Aluno();
            case MOTORISTA -> new Motorista();
            case ADMIN -> new Pessoa();
        };
        pessoa.setNome(tipo == TipoPessoa.MOTORISTA ? "Motorista" : tipo.name());
        pessoa.setTipoPessoa(tipo);
        em.persist(pessoa);
        Usuario u = new Usuario();
        u.setPessoa(pessoa); u.setTipoPessoa(tipo); u.setEmail(email);
        u.setSenha(encoder.encode("SenhaTeste123")); u.setAtivo(ativo); em.persist(u); return u;
    }
    private void viagem(DiaSemana dia, LocalDate data, Faculdade... destinos) {
        Rota rota = new Rota(); rota.setNome("Rota central"); rota.setDescricao("Centro → Universidades");
        rota.setFaculdade(faculdade); rota.setPontosEmbarque(new ArrayList<>()); em.persist(rota);
        GradeDiaria grade = null;
        if (dia != null) { grade = new GradeDiaria(); grade.setDiaSemana(dia); grade.setDescricao("Grade de " + dia); em.persist(grade); }
        Viagem v = new Viagem(); v.setData(data); v.setGradeDiaria(grade); v.setRota(rota); v.setOnibus(onibus);
        v.setMotorista((Motorista) motorista.getPessoa()); v.setFaculdades(new ArrayList<>(Arrays.asList(destinos))); em.persist(v);
    }
    private String token(Usuario usuario) {
        return "Bearer " + auth.login(new LoginRequest(usuario.getEmail(), "SenhaTeste123")).accessToken();
    }
    private void autenticar(Usuario usuario) {
        SecurityContextHolder.getContext().setAuthentication(converter.convert(decoder.decode(token(usuario).substring(7))));
    }
}
