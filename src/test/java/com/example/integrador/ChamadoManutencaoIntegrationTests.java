package com.example.integrador;

import com.example.integrador.DTO.*;
import com.example.integrador.Enum.*;
import com.example.integrador.Model.*;
import com.example.integrador.Repository.*;
import com.example.integrador.Service.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ChamadoManutencaoIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired UsuarioService usuarios;
    @Autowired AuthService auth;
    @Autowired PessoaRepository pessoas;
    @Autowired OnibusRepository onibusRepository;
    @Autowired ChamadoManutencaoRepository chamados;
    @Autowired ViagemRepository viagens;
    @Autowired GradeDiariaService gradeService;
    @Autowired ChamadoManutencaoService chamadoService;
    @Autowired EntityManager em;

    private Aluno aluno;
    private Motorista motorista;
    private UsuarioResponse contaAluno;
    private String adminToken;
    private String alunoToken;
    private String motoristaToken;
    private Onibus onibus;

    @BeforeEach
    void preparar() {
        usuarios.criarAdmin(new CriarAdminRequest("Admin Manutenção", "admin@manutencao.test", "SenhaAdmin123"));
        adminToken = token("admin@manutencao.test");
        aluno = new Aluno();
        aluno.setNome("Aluno Solicitante");
        aluno.setTipoPessoa(TipoPessoa.ALUNO);
        aluno.setCpfCnpj("cpf-nao-publico");
        aluno.setTelefone("telefone-nao-publico");
        pessoas.save(aluno);
        contaAluno = usuarios.criarUsuario(new CriarUsuarioRequest(aluno.getId(), "aluno@manutencao.test", "SenhaAdmin123"));
        alunoToken = token("aluno@manutencao.test");
        motorista = new Motorista();
        motorista.setNome("Motorista Solicitante");
        motorista.setTipoPessoa(TipoPessoa.MOTORISTA);
        pessoas.save(motorista);
        usuarios.criarUsuario(new CriarUsuarioRequest(motorista.getId(), "motorista@manutencao.test", "SenhaAdmin123"));
        motoristaToken = token("motorista@manutencao.test");
        onibus = criarOnibus(StatusOnibus.OPERANDO);
    }

    @Test
    void alunoAbreComAutoriaDoLoginEIgnoraCamposProtegidos() throws Exception {
        mvc.perform(post("/chamados-manutencao").header("Authorization", alunoToken)
                        .contentType("application/json").content("""
                        {"onibusId":%d,"descricao":"  Ar-condicionado quebrado  ",
                         "solicitante":{"id":%d},"responsavel":{"id":%d},
                         "pessoaId":%d,"nome":"Falso","dataAbertura":"2000-01-01",
                         "status":"RESOLVIDO","urgencia":"ALTA","observacaoAdmin":"Falsa"}
                        """.formatted(onibus.getId(), motorista.getId(), motorista.getId(), motorista.getId())))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.descricao").value("Ar-condicionado quebrado"))
                .andExpect(jsonPath("$.dataAbertura").value(LocalDate.now().toString()))
                .andExpect(jsonPath("$.status").value("ABERTO"))
                .andExpect(jsonPath("$.urgencia").value("MEDIA"))
                .andExpect(jsonPath("$.observacaoAdmin").isEmpty())
                .andExpect(jsonPath("$.solicitante.pessoaId").value(aluno.getId()))
                .andExpect(jsonPath("$.solicitante.nome").value(aluno.getNome()))
                .andExpect(jsonPath("$.solicitante.tipoPessoa").value("ALUNO"));
        assertThat(chamados.findAll()).singleElement().satisfies(c -> {
            assertThat(c.getSolicitante().getId()).isEqualTo(aluno.getId());
            assertThat(c.getOnibus().getId()).isEqualTo(onibus.getId());
        });
    }

    @Test
    void motoristaPodeAbrirParaQualquerOnibusESomenteRecebeResumoSeguro() throws Exception {
        onibus.setStatusOnibus(StatusOnibus.INATIVO);
        Long id = abrir(motoristaToken, motorista.getId());
        mvc.perform(get("/chamados-manutencao/{id}", id).header("Authorization", motoristaToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.solicitante.tipoPessoa").value("MOTORISTA"))
                .andExpect(jsonPath("$.solicitante.cpfCnpj").doesNotExist())
                .andExpect(jsonPath("$.solicitante.telefone").doesNotExist())
                .andExpect(jsonPath("$.solicitante.senha").doesNotExist())
                .andExpect(jsonPath("$.onibus.fotoUrl").doesNotExist())
                .andExpect(jsonPath("$.onibus.statusOnibus").value("INATIVO"));
        for (String token : List.of(alunoToken, motoristaToken, adminToken)) {
            mvc.perform(get("/chamados-manutencao/onibus").header("Authorization", token))
                    .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(onibus.getId()))
                    .andExpect(jsonPath("$[0].fotoUrl").doesNotExist());
        }
        assertThat(onibus.getStatusOnibus()).isEqualTo(StatusOnibus.INATIVO);
    }

    @Test
    void isolamentoFiltrosEOrdenacao() throws Exception {
        Long alunoId = abrir(alunoToken, aluno.getId());
        Long motoristaId = abrir(motoristaToken, motorista.getId());
        mvc.perform(get("/chamados-manutencao/minhas").param("pessoaId", motorista.getId().toString())
                        .header("Authorization", alunoToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(alunoId));
        mvc.perform(get("/chamados-manutencao/{id}", motoristaId).header("Authorization", alunoToken))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
        mvc.perform(get("/chamados-manutencao/{id}", alunoId).header("Authorization", motoristaToken))
                .andExpect(status().isNotFound());
        mvc.perform(get("/chamados-manutencao").param("status", "ABERTO").param("urgencia", "MEDIA")
                        .param("onibusId", onibus.getId().toString()).header("Authorization", adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(motoristaId));
        mvc.perform(get("/chamados-manutencao").param("status", "RESOLVIDO").header("Authorization", adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/chamados-manutencao").param("urgencia", "ALTA").header("Authorization", adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/chamados-manutencao").param("onibusId", "99999999").header("Authorization", adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void matrizDePermissoesEServicoProtegido() throws Exception {
        Long id = abrir(alunoToken, aluno.getId());
        for (String caminho : List.of("/chamados-manutencao", "/chamados-manutencao/minhas",
                "/chamados-manutencao/onibus", "/chamados-manutencao/" + id)) {
            mvc.perform(get(caminho)).andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/chamados-manutencao").contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(put("/chamados-manutencao/{id}/atendimento", id).contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/chamados-manutencao").header("Authorization", adminToken)
                        .contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/chamados-manutencao/minhas").header("Authorization", adminToken))
                .andExpect(status().isForbidden());
        for (String token : List.of(alunoToken, motoristaToken)) {
            mvc.perform(get("/chamados-manutencao").header("Authorization", token)).andExpect(status().isForbidden());
            mvc.perform(put("/chamados-manutencao/{id}/atendimento", id).header("Authorization", token)
                            .contentType("application/json").content("{}"))
                    .andExpect(status().isForbidden());
            mvc.perform(delete("/chamados-manutencao/{id}", id).header("Authorization", token))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/onibus/listar").header("Authorization", token)).andExpect(status().isForbidden());
        }
        mvc.perform(delete("/chamados-manutencao/{id}", id).header("Authorization", adminToken))
                .andExpect(status().isMethodNotAllowed());
        assertThatThrownBy(() -> chamadoService.listar(null, null, null))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    @Test
    void atendimentoIndependenteEstadoDoOnibusEReabertura() throws Exception {
        Long id = abrir(alunoToken, aluno.getId());
        atender(id, "{\"urgencia\":\"ALTA\",\"status\":\"EM_ANDAMENTO\",\"observacaoAdmin\":\"Avaliação\"}");
        assertThat(onibus.getStatusOnibus()).isEqualTo(StatusOnibus.OPERANDO);
        atender(id, "{\"urgencia\":\"ALTA\",\"status\":\"EM_ANDAMENTO\",\"observacaoAdmin\":\"Oficina\",\"statusOnibus\":\"MANUTENCAO\"}");
        em.flush();
        assertThat(onibus.getStatusOnibus()).isEqualTo(StatusOnibus.MANUTENCAO);
        assertThat(onibus.getPlaca()).isEqualTo("ABC1234");
        assertThat(onibus.getModelo()).isEqualTo("Modelo Teste");
        assertThat(onibus.getCapacidade()).isEqualTo(40);
        assertThat(onibus.getFotoUrl()).isEqualTo("foto-nao-alterar");
        atender(id, "{\"urgencia\":\"BAIXA\",\"status\":\"RESOLVIDO\",\"observacaoAdmin\":\"Conserto concluído\"}");
        assertThat(onibus.getStatusOnibus()).isEqualTo(StatusOnibus.MANUTENCAO);
        mvc.perform(get("/chamados-manutencao/{id}", id).header("Authorization", alunoToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.observacaoAdmin").value("Conserto concluído"));
        atender(id, "{\"urgencia\":\"MEDIA\",\"status\":\"ABERTO\",\"observacaoAdmin\":\"Reaberto\"}");
        assertThat(chamados.findById(id).orElseThrow().getStatus()).isEqualTo(StatusChamado.ABERTO);
        assertThat(chamados.findById(id).orElseThrow().getDescricao()).isEqualTo("Ar quebrado");
        atender(id, "{\"urgencia\":\"MEDIA\",\"status\":\"RESOLVIDO\",\"observacaoAdmin\":\"Liberado\",\"statusOnibus\":\"OPERANDO\"}");
        assertThat(onibus.getStatusOnibus()).isEqualTo(StatusOnibus.OPERANDO);
    }

    @Test
    void validacaoEAtendimentoInvalidoNaoAlteramOnibus() throws Exception {
        Long id = abrir(alunoToken, aluno.getId());
        for (String corpo : List.of("{}", "{\"urgencia\":\"ALTA\",\"status\":\"RESOLVIDO\",\"observacaoAdmin\":\"   \",\"statusOnibus\":\"QUEBRADO\"}",
                "{\"urgencia\":\"INVALIDA\",\"status\":\"ABERTO\"}",
                "{\"urgencia\":\"MEDIA\",\"status\":\"ABERTO\",\"observacaoAdmin\":\"" + "x".repeat(2001) + "\"}")) {
            mvc.perform(put("/chamados-manutencao/{id}/atendimento", id).header("Authorization", adminToken)
                            .contentType("application/json").content(corpo))
                    .andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        }
        assertThat(onibus.getStatusOnibus()).isEqualTo(StatusOnibus.OPERANDO);
        assertThat(chamados.findById(id).orElseThrow().getStatus()).isEqualTo(StatusChamado.ABERTO);
        for (String corpo : List.of("{}", "{\"onibusId\":0,\"descricao\":\"Teste\"}",
                "{\"onibusId\":" + onibus.getId() + ",\"descricao\":\"  \"}",
                "{\"onibusId\":" + onibus.getId() + ",\"descricao\":\"" + "x".repeat(2001) + "\"}")) {
            mvc.perform(post("/chamados-manutencao").header("Authorization", alunoToken)
                            .contentType("application/json").content(corpo)).andExpect(status().isBadRequest());
        }
        mvc.perform(post("/chamados-manutencao").header("Authorization", alunoToken).contentType("application/json")
                        .content("{\"onibusId\":99999999,\"descricao\":\"Teste\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/chamados-manutencao/99999999").header("Authorization", adminToken)).andExpect(status().isNotFound());
        mvc.perform(put("/chamados-manutencao/99999999/atendimento").header("Authorization", adminToken).contentType("application/json")
                        .content("{\"urgencia\":\"MEDIA\",\"status\":\"ABERTO\"}")).andExpect(status().isNotFound());
        mvc.perform(get("/chamados-manutencao").param("status", "INVALIDO").header("Authorization", adminToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void excluirAcessoPreservaAutoriaEBloqueiaExclusaoFisica() throws Exception {
        Long id = abrir(alunoToken, aluno.getId());
        abrir(motoristaToken, motorista.getId());
        mvc.perform(delete("/usuarios/{id}", contaAluno.id()).header("Authorization", adminToken))
                .andExpect(status().isNoContent());
        em.flush();
        assertThat(pessoas.existsById(aluno.getId())).isTrue();
        mvc.perform(get("/chamados-manutencao/{id}", id).header("Authorization", adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.solicitante.nome").value(aluno.getNome()));
        mvc.perform(get("/chamados-manutencao/minhas").header("Authorization", alunoToken)).andExpect(status().isUnauthorized());
        for (String caminho : List.of("/aluno/deletar-aluno/" + aluno.getId(),
                "/motorista/deletar-motorista/" + motorista.getId(), "/onibus/deletar-onibus/" + onibus.getId())) {
            mvc.perform(delete(caminho).header("Authorization", adminToken))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.detail").exists());
        }
        assertThat(chamados.existsById(id)).isTrue();
        assertThat(onibusRepository.existsById(onibus.getId())).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = StatusOnibus.class, names = {"MANUTENCAO", "QUEBRADO", "INATIVO"})
    void bloqueiaNovaViagemETrocaMasPreservaViagemExistente(StatusOnibus estado) throws Exception {
        Viagem viagem = new Viagem();
        viagem.setOnibus(onibus);
        viagem.setData(LocalDate.now());
        viagens.saveAndFlush(viagem);
        onibus.setStatusOnibus(estado);
        mvc.perform(post("/viagem/salvar-viagem").header("Authorization", adminToken).contentType("application/json")
                        .content("{\"onibusId\":" + onibus.getId() + "}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(409));
        mvc.perform(put("/viagem/atualizar-viagem/{id}", viagem.getId()).header("Authorization", adminToken).contentType("application/json")
                        .content("{\"onibusId\":" + onibus.getId() + ",\"data\":\"2026-12-01\"}"))
                .andExpect(status().isOk());
        Onibus outro = criarOnibus(estado);
        mvc.perform(put("/viagem/atualizar-viagem/{id}", viagem.getId()).header("Authorization", adminToken).contentType("application/json")
                        .content("{\"onibusId\":" + outro.getId() + "}"))
                .andExpect(status().isConflict());
        assertThat(viagens.findById(viagem.getId()).orElseThrow().getOnibus().getId()).isEqualTo(onibus.getId());
    }

    @Test
    void onibusOperandoPodeSerAtribuidoENaoExistenteRetorna404() throws Exception {
        mvc.perform(post("/viagem/salvar-viagem").header("Authorization", adminToken).contentType("application/json")
                        .content("{\"onibusId\":" + onibus.getId() + "}"))
                .andExpect(status().isOk());
        mvc.perform(post("/viagem/salvar-viagem").header("Authorization", adminToken).contentType("application/json")
                        .content("{\"onibusId\":99999999}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void gradeTambemValidaOnibusEPreservaIdentidadeDasViagensExistentes() throws Exception {
        ViagemDTO v = new ViagemDTO();
        v.setOnibusId(onibus.getId());
        GradeDiariaDTO dto = new GradeDiariaDTO();
        dto.setDiaSemana(DiaSemana.SEGUNDA);
        dto.setViagens(List.of(v));
        GradeDiariaDTO salva = gradeService.salvarGrade(dto);
        em.flush();
        Long viagemId = salva.getViagens().getFirst().getId();
        onibus.setStatusOnibus(StatusOnibus.QUEBRADO);
        mvc.perform(post("/grade-diaria/salvar-grade").header("Authorization", adminToken).contentType("application/json")
                        .content("{\"viagens\":[{\"onibusId\":" + onibus.getId() + "}]}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(409));
        mvc.perform(put("/grade-diaria/atualizar-grade/{id}", salva.getId()).header("Authorization", adminToken).contentType("application/json")
                        .content("{\"descricao\":\"Atualizada\",\"viagens\":[{\"id\":" + viagemId + ",\"onibusId\":" + onibus.getId() + "}]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.viagens[0].id").value(viagemId));
        em.flush();
        assertThat(viagens.existsById(viagemId)).isTrue();
        Onibus outro = criarOnibus(StatusOnibus.MANUTENCAO);
        mvc.perform(put("/grade-diaria/atualizar-grade/{id}", salva.getId()).header("Authorization", adminToken).contentType("application/json")
                        .content("{\"viagens\":[{\"id\":" + viagemId + ",\"onibusId\":" + outro.getId() + "}]}"))
                .andExpect(status().isConflict());
    }

    private Onibus criarOnibus(StatusOnibus status) {
        Onibus o = new Onibus();
        o.setNumeroIdentificacao("01"); o.setPlaca("ABC1234"); o.setModelo("Modelo Teste");
        o.setCapacidade(40); o.setFotoUrl("foto-nao-alterar"); o.setStatusOnibus(status);
        return onibusRepository.save(o);
    }

    private Long abrir(String token, Long pessoaId) throws Exception {
        mvc.perform(post("/chamados-manutencao").header("Authorization", token).contentType("application/json")
                        .content("{\"onibusId\":" + onibus.getId() + ",\"descricao\":\"Ar quebrado\"}"))
                .andExpect(status().isCreated());
        return chamados.findAllBySolicitanteIdOrderByDataAberturaDescIdDesc(pessoaId).getFirst().getId();
    }

    private void atender(Long id, String corpo) throws Exception {
        mvc.perform(put("/chamados-manutencao/{id}/atendimento", id).header("Authorization", adminToken)
                        .contentType("application/json").content(corpo)).andExpect(status().isOk());
    }

    private String token(String email) { return "Bearer " + auth.login(new LoginRequest(email, "SenhaAdmin123")).accessToken(); }
}
