package com.example.integrador;

import com.example.integrador.DTO.AlunoDTO;
import com.example.integrador.DTO.CriarAdminRequest;
import com.example.integrador.DTO.CriarUsuarioRequest;
import com.example.integrador.DTO.LoginRequest;
import com.example.integrador.DTO.LoginResponse;
import com.example.integrador.DTO.UsuarioResponse;
import com.example.integrador.Enum.StatusFinanceiro;
import com.example.integrador.Enum.StatusMatricula;
import com.example.integrador.Model.Aluno;
import com.example.integrador.Model.ContaReceber;
import com.example.integrador.Repository.AlunoRepository;
import com.example.integrador.Repository.ContaReceberRepository;
import com.example.integrador.Repository.RecebimentoRepository;
import com.example.integrador.Service.AlunoService;
import com.example.integrador.Service.AuthService;
import com.example.integrador.Service.RotinaFinanceiraService;
import com.example.integrador.Service.UsuarioService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MatriculaFinanceiraIntegrationTests {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AlunoService alunoService;
    @Autowired
    private UsuarioService usuarioService;
    @Autowired
    private AuthService authService;
    @Autowired
    private AlunoRepository alunoRepository;
    @Autowired
    private ContaReceberRepository contaReceberRepository;
    @Autowired
    private RecebimentoRepository recebimentoRepository;
    @Autowired
    private RotinaFinanceiraService rotinaFinanceiraService;

    @Test
    void cadastroDoAlunoGeraPrimeiraMatriculaComVencimentoEmTrintaDias() {
        Aluno aluno = criarAluno("Aluno Novo", "1001", "250.00");

        ContaReceber conta = contaReceberRepository
                .findTopByAlunoIdOrderByDataVencimentoDesc(aluno.getId());

        assertThat(conta).isNotNull();
        assertThat(conta.getDescricao()).isEqualTo("Matrícula - Aluno Novo");
        assertThat(conta.getValor()).isEqualByComparingTo("250.00");
        assertThat(conta.getDataVencimento()).isEqualTo(LocalDate.now().plusDays(30));
        assertThat(conta.getStatus()).isEqualTo(StatusFinanceiro.PENDENTE);
        assertThat(aluno.getStatusMatricula()).isEqualTo(StatusMatricula.PENDENTE);
        assertThat(aluno.getDataCadastro()).isEqualTo(LocalDate.now());
    }

    @Test
    void alunoListaEPagaSomenteSuaPropriaMatricula() throws Exception {
        Aluno aluno = criarAluno("Aluno Pagador", "2001", "320.00");
        ContaReceber conta = contaReceberRepository
                .findTopByAlunoIdOrderByDataVencimentoDesc(aluno.getId());
        String tokenAluno = criarContaEToken(aluno, "pagador@email.com");

        Aluno outroAluno = criarAluno("Outro Aluno", "2002", "410.00");
        ContaReceber contaOutro = contaReceberRepository
                .findTopByAlunoIdOrderByDataVencimentoDesc(outroAluno.getId());
        String tokenOutro = criarContaEToken(outroAluno, "outro@email.com");

        mockMvc.perform(get("/contareceber/minhas")
                        .header("Authorization", bearer(tokenAluno)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(conta.getId()))
                .andExpect(jsonPath("$[0].valor").value(320.00))
                .andExpect(jsonPath("$[0].status").value("PENDENTE"));

        mockMvc.perform(post("/recebimentos/realizar/{id}", conta.getId())
                        .param("formaPagamento", "PIX")
                        .header("Authorization", bearer(tokenAluno)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contaId").value(conta.getId()))
                .andExpect(jsonPath("$.valor").value(320.00))
                .andExpect(jsonPath("$.formaPagamento").value("PIX"))
                .andExpect(jsonPath("$.nomeAluno").value("Aluno Pagador"));

        assertThat(contaReceberRepository.findById(conta.getId()).orElseThrow().getStatus())
                .isEqualTo(StatusFinanceiro.PAGO);
        assertThat(recebimentoRepository.findByContaReceberId(conta.getId())).isPresent();
        assertThat(alunoRepository.findById(aluno.getId()).orElseThrow().getStatusMatricula())
                .isEqualTo(StatusMatricula.ATIV0);

        mockMvc.perform(get("/recebimentos/recibo/conta/{id}", conta.getId())
                        .header("Authorization", bearer(tokenAluno)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contaId").value(conta.getId()));
        mockMvc.perform(post("/recebimentos/realizar/{id}", conta.getId())
                        .param("formaPagamento", "PIX")
                        .header("Authorization", bearer(tokenAluno)))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/recebimentos/realizar/{id}", contaOutro.getId())
                        .param("formaPagamento", "PIX")
                        .header("Authorization", bearer(tokenAluno)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/recebimentos/recibo/conta/{id}", conta.getId())
                        .header("Authorization", bearer(tokenOutro)))
                .andExpect(status().isNotFound());
    }

    @Test
    void rotinaGeraUmaNovaMatriculaQuandoAUltimaVenceSemDuplicar() {
        Aluno aluno = criarAluno("Aluno Recorrente", "3001", "180.00");
        ContaReceber primeira = contaReceberRepository
                .findTopByAlunoIdOrderByDataVencimentoDesc(aluno.getId());
        primeira.setDataVencimento(LocalDate.now());
        contaReceberRepository.saveAndFlush(primeira);

        rotinaFinanceiraService.gerarContasRecorrentes();

        List<ContaReceber> contas = contaReceberRepository
                .findAllByAlunoIdOrderByDataVencimentoDesc(aluno.getId());
        assertThat(contas).hasSize(2);
        assertThat(contas.getFirst().getDataVencimento()).isEqualTo(LocalDate.now().plusDays(30));
        assertThat(contas.getFirst().getValor()).isEqualByComparingTo("180.00");
        assertThat(contas.getFirst().getStatus()).isEqualTo(StatusFinanceiro.PENDENTE);

        rotinaFinanceiraService.gerarContasRecorrentes();
        assertThat(contaReceberRepository.findAllByAlunoIdOrderByDataVencimentoDesc(aluno.getId()))
                .hasSize(2);
    }

    @Test
    void endpointsExigemJwtDeAlunoEValidamMensalidade() throws Exception {
        mockMvc.perform(get("/contareceber/minhas"))
                .andExpect(status().isUnauthorized());

        UsuarioResponse admin = usuarioService.criarAdmin(
                new CriarAdminRequest("Administrador Financeiro", "financeiro@admin.com", "SenhaAdmin123"));
        String tokenAdmin = token("financeiro@admin.com", "SenhaAdmin123");
        mockMvc.perform(get("/contareceber/minhas")
                        .header("Authorization", bearer(tokenAdmin)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/aluno/salvar-aluno")
                        .header("Authorization", bearer(tokenAdmin))
                        .contentType("application/json")
                        .content("{\"nome\":\"Sem mensalidade\",\"matricula\":\"4001\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.mensalidade").exists());
    }

    private Aluno criarAluno(String nome, String matricula, String mensalidade) {
        AlunoDTO dto = new AlunoDTO();
        dto.setNome(nome);
        dto.setMatricula(matricula);
        dto.setMensalidade(new BigDecimal(mensalidade));
        var anterior = org.springframework.security.core.context.SecurityContextHolder.getContext();
        var contexto = org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
        contexto.setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                "admin-fixture", null, List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"))));
        try {
            org.springframework.security.core.context.SecurityContextHolder.setContext(contexto);
            return alunoService.salvarAluno(dto);
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.setContext(anterior);
        }
    }

    private String criarContaEToken(Aluno aluno, String email) {
        usuarioService.criarUsuario(new CriarUsuarioRequest(
                aluno.getId(), email, "SenhaAluno123"));
        return token(email, "SenhaAluno123");
    }

    private String token(String email, String senha) {
        LoginResponse response = authService.login(new LoginRequest(email, senha));
        return response.accessToken();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
