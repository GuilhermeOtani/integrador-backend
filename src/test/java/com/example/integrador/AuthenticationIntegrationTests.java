package com.example.integrador;

import com.example.integrador.DTO.CriarAdminRequest;
import com.example.integrador.DTO.CriarUsuarioRequest;
import com.example.integrador.DTO.AtualizarEmailUsuarioRequest;
import com.example.integrador.DTO.AtualizarStatusUsuarioRequest;
import com.example.integrador.DTO.LoginRequest;
import com.example.integrador.DTO.LoginResponse;
import com.example.integrador.DTO.RedefinirSenhaUsuarioRequest;
import com.example.integrador.DTO.UsuarioResponse;
import com.example.integrador.Enum.TipoPessoa;
import com.example.integrador.Exception.ConflitoException;
import com.example.integrador.Model.Aluno;
import com.example.integrador.Model.Motorista;
import com.example.integrador.Model.Usuario;
import com.example.integrador.Model.Viagem;
import com.example.integrador.Repository.PessoaRepository;
import com.example.integrador.Repository.UsuarioRepository;
import com.example.integrador.Repository.ViagemRepository;
import com.example.integrador.Service.AuthService;
import com.example.integrador.Service.UsuarioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthenticationIntegrationTests {
    private static final String ADMIN_EMAIL = "admin@integrador.test";
    private static final String ADMIN_PASSWORD = "SenhaAdmin123";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private PessoaRepository pessoaRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private ViagemRepository viagemRepository;
    @Autowired
    private UsuarioService usuarioService;
    @Autowired
    private AuthService authService;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JwtDecoder jwtDecoder;
    @Autowired
    private JwtEncoder jwtEncoder;

    private UsuarioResponse admin;

    @BeforeEach
    void criarAdmin() {
        admin = usuarioService.criarAdmin(
                new CriarAdminRequest("Administrador", ADMIN_EMAIL, ADMIN_PASSWORD));
    }

    @Test
    void criaContaVinculadaDerivaPapelEArmazenaBcrypt() {
        Aluno aluno = salvarAluno("Aluno Um");

        UsuarioResponse response = usuarioService.criarUsuario(
                new CriarUsuarioRequest(aluno.getId(), "ALUNO@EMAIL.COM", "SenhaAluno123"));

        Usuario salvo = usuarioRepository.findById(response.id()).orElseThrow();
        assertThat(response.tipoPessoa()).isEqualTo(TipoPessoa.ALUNO);
        assertThat(response.pessoaId()).isEqualTo(aluno.getId());
        assertThat(salvo.getEmail()).isEqualTo("aluno@email.com");
        assertThat(salvo.getSenha()).startsWith("$2");
        assertThat(passwordEncoder.matches("SenhaAluno123", salvo.getSenha())).isTrue();
        assertThat(salvo.getSenha()).isNotEqualTo("SenhaAluno123");
        assertThat(response.ativo()).isTrue();
        assertThat(salvo.getTokenVersion()).isZero();
    }

    @Test
    void rejeitaEmailOuPessoaDuplicados() {
        Motorista motorista = salvarMotorista("Motorista Um");
        usuarioService.criarUsuario(
                new CriarUsuarioRequest(motorista.getId(), "motorista@email.com", "SenhaMotorista123"));

        assertThatThrownBy(() -> usuarioService.criarUsuario(
                new CriarUsuarioRequest(motorista.getId(), "outro@email.com", "OutraSenha123")))
                .isInstanceOf(ConflitoException.class);

        Aluno aluno = salvarAluno("Aluno Dois");
        assertThatThrownBy(() -> usuarioService.criarUsuario(
                new CriarUsuarioRequest(aluno.getId(), "MOTORISTA@EMAIL.COM", "OutraSenha123")))
                .isInstanceOf(ConflitoException.class);
    }

    @Test
    void loginValidoRetornaJwtAssinadoComClaimsEDuasHoras() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType("application/json")
                        .content("{\"email\":\"ADMIN@INTEGRADOR.TEST\",\"senha\":\"SenhaAdmin123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(7200))
                .andExpect(jsonPath("$.usuario.email").value(ADMIN_EMAIL))
                .andExpect(jsonPath("$.usuario.tipoPessoa").value("ADMIN"))
                .andExpect(jsonPath("$.usuario.ativo").value(true))
                .andExpect(jsonPath("$.usuario.tokenVersion").doesNotExist())
                .andExpect(jsonPath("$.usuario.senha").doesNotExist());

        Jwt jwt = jwtDecoder.decode(tokenAdmin());
        assertThat(jwt.getSubject()).isEqualTo(admin.id().toString());
        assertThat(jwt.getClaimAsString("email")).isEqualTo(ADMIN_EMAIL);
        assertThat(jwt.<Long>getClaim("pessoaId")).isEqualTo(admin.pessoaId());
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("ADMIN");
        assertThat(jwt.<Long>getClaim("ver")).isZero();
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()).getSeconds()).isEqualTo(7200);
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("integrador-api");
    }

    @Test
    void loginInvalidoRetornaMensagemGenerica() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType("application/json")
                        .content("{\"email\":\"admin@integrador.test\",\"senha\":\"senha-errada\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("E-mail ou senha inválidos"));

        mockMvc.perform(post("/auth/login")
                        .contentType("application/json")
                        .content("{\"email\":\"inexistente@integrador.test\",\"senha\":\"qualquer-senha\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("E-mail ou senha inválidos"));
    }

    @Test
    void aplicaMatrizDePermissoesESerializaSemSenha() throws Exception {
        Aluno aluno = salvarAluno("Aluno Leitor");
        usuarioService.criarUsuario(
                new CriarUsuarioRequest(aluno.getId(), "leitor@email.com", "SenhaAluno123"));
        String tokenAluno = token("leitor@email.com", "SenhaAluno123");

        Motorista motorista = salvarMotorista("Motorista Restrito");
        usuarioService.criarUsuario(
                new CriarUsuarioRequest(motorista.getId(), "restrito@email.com", "SenhaMotorista123"));
        String tokenMotorista = token("restrito@email.com", "SenhaMotorista123");

        mockMvc.perform(get("/rota/listar"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/auth/me").header("Authorization", bearer(tokenAluno)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipoPessoa").value("ALUNO"));
        mockMvc.perform(get("/auth/me").header("Authorization", bearer(tokenMotorista)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipoPessoa").value("MOTORISTA"));
        mockMvc.perform(get("/auth/me").header("Authorization", bearer(tokenAdmin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipoPessoa").value("ADMIN"));
        mockMvc.perform(get("/rota/listar").header("Authorization", bearer(tokenAluno)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/rota/listar").header("Authorization", bearer(tokenMotorista)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/aluno/listar").header("Authorization", bearer(tokenAluno)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/aluno/listar").header("Authorization", bearer(tokenAdmin())))
                .andExpect(status().isOk());
        mockMvc.perform(get("/usuarios").header("Authorization", bearer(tokenAluno)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/viagem/minhas").header("Authorization", bearer(tokenAdmin())))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/contapagar/listar").header("Authorization", bearer(tokenAluno)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/contapagar/listar").header("Authorization", bearer(tokenAdmin())))
                .andExpect(status().isOk());
        mockMvc.perform(get("/usuarios").header("Authorization", bearer(tokenAdmin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ativo").value(true))
                .andExpect(jsonPath("$[0].senha").doesNotExist())
                .andExpect(jsonPath("$[0].tokenVersion").doesNotExist());
    }

    @Test
    void administraContaERevogaTokensAposCadaAlteracaoSensivel() throws Exception {
        Aluno aluno = salvarAluno("Aluno Administrado");
        UsuarioResponse conta = usuarioService.criarUsuario(new CriarUsuarioRequest(
                aluno.getId(), "conta@email.com", "SenhaInicial123"));
        String adminToken = tokenAdmin();
        String tokenAntigo = token("conta@email.com", "SenhaInicial123");

        mockMvc.perform(patch("/usuarios/{id}", conta.id())
                        .header("Authorization", bearer(adminToken))
                        .contentType("application/json")
                        .content("{\"email\":\"NOVO@EMAIL.COM\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("novo@email.com"))
                .andExpect(jsonPath("$.ativo").value(true));
        mockMvc.perform(get("/auth/me").header("Authorization", bearer(tokenAntigo)))
                .andExpect(status().isUnauthorized());

        String tokenAposEmail = token("novo@email.com", "SenhaInicial123");
        mockMvc.perform(put("/usuarios/{id}/senha", conta.id())
                        .header("Authorization", bearer(adminToken))
                        .contentType("application/json")
                        .content("{\"senha\":\"SenhaNova123\"}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/auth/me").header("Authorization", bearer(tokenAposEmail)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/auth/login")
                        .contentType("application/json")
                        .content("{\"email\":\"novo@email.com\",\"senha\":\"SenhaInicial123\"}"))
                .andExpect(status().isUnauthorized());

        String tokenAposSenha = token("novo@email.com", "SenhaNova123");
        mockMvc.perform(patch("/usuarios/{id}/status", conta.id())
                        .header("Authorization", bearer(adminToken))
                        .contentType("application/json")
                        .content("{\"ativo\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(false));
        mockMvc.perform(get("/auth/me").header("Authorization", bearer(tokenAposSenha)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/auth/login")
                        .contentType("application/json")
                        .content("{\"email\":\"novo@email.com\",\"senha\":\"SenhaNova123\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("E-mail ou senha inválidos"));

        mockMvc.perform(patch("/usuarios/{id}/status", conta.id())
                        .header("Authorization", bearer(adminToken))
                        .contentType("application/json")
                        .content("{\"ativo\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(true));
        String tokenReativado = token("novo@email.com", "SenhaNova123");

        mockMvc.perform(delete("/usuarios/{id}", conta.id())
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/auth/me").header("Authorization", bearer(tokenReativado)))
                .andExpect(status().isUnauthorized());

        assertThat(usuarioRepository.findById(conta.id())).isEmpty();
        assertThat(pessoaRepository.findById(aluno.getId())).isPresent();
        assertThat(usuarioService.listarPessoasSemUsuario())
                .extracting(pessoa -> pessoa.id())
                .contains(aluno.getId());
    }

    @Test
    void alteracoesSaoIdempotentesNormalizadasEProtegemAdministradores() throws Exception {
        Usuario adminSalvo = usuarioRepository.findById(admin.id()).orElseThrow();

        usuarioService.atualizarEmail(admin.id(), new AtualizarEmailUsuarioRequest(" ADMIN@INTEGRADOR.TEST "));
        usuarioService.atualizarStatus(admin.id(), new AtualizarStatusUsuarioRequest(true), -1L);
        assertThat(adminSalvo.getTokenVersion()).isZero();

        assertThatCode(() -> usuarioService.atualizarEmail(
                admin.id(), new AtualizarEmailUsuarioRequest("admin@integrador.test")))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> usuarioService.atualizarStatus(
                admin.id(), new AtualizarStatusUsuarioRequest(false), admin.id()))
                .isInstanceOf(ConflitoException.class)
                .hasMessageContaining("própria conta");
        assertThatThrownBy(() -> usuarioService.excluirAcesso(admin.id(), admin.id()))
                .isInstanceOf(ConflitoException.class)
                .hasMessageContaining("própria conta");
        assertThatThrownBy(() -> usuarioService.atualizarStatus(
                admin.id(), new AtualizarStatusUsuarioRequest(false), -1L))
                .isInstanceOf(ConflitoException.class)
                .hasMessageContaining("último administrador");
        assertThatThrownBy(() -> usuarioService.excluirAcesso(admin.id(), -1L))
                .isInstanceOf(ConflitoException.class)
                .hasMessageContaining("último administrador");
    }

    @Test
    void validaConflitosPermissoesBcryptEIncrementoDeVersao() throws Exception {
        Aluno primeiro = salvarAluno("Primeiro");
        Aluno segundo = salvarAluno("Segundo");
        UsuarioResponse contaUm = usuarioService.criarUsuario(new CriarUsuarioRequest(
                primeiro.getId(), "primeiro@email.com", "SenhaPrimeiro123"));
        UsuarioResponse contaDois = usuarioService.criarUsuario(new CriarUsuarioRequest(
                segundo.getId(), "segundo@email.com", "SenhaSegundo123"));

        assertThatThrownBy(() -> usuarioService.atualizarEmail(
                contaDois.id(), new AtualizarEmailUsuarioRequest(" PRIMEIRO@EMAIL.COM ")))
                .isInstanceOf(ConflitoException.class);

        usuarioService.redefinirSenha(contaUm.id(), new RedefinirSenhaUsuarioRequest("OutraSenha123"));
        Usuario atualizado = usuarioRepository.findById(contaUm.id()).orElseThrow();
        assertThat(atualizado.getTokenVersion()).isEqualTo(1);
        assertThat(passwordEncoder.matches("OutraSenha123", atualizado.getSenha())).isTrue();
        assertThat(atualizado.getSenha()).isNotEqualTo("OutraSenha123");

        mockMvc.perform(patch("/usuarios/{id}", contaUm.id())
                        .header("Authorization", bearer(token("segundo@email.com", "SenhaSegundo123")))
                        .contentType("application/json")
                        .content("{\"email\":\"sem-permissao@email.com\"}"))
                .andExpect(status().isForbidden());

        String adminToken = tokenAdmin();
        mockMvc.perform(put("/usuarios/{id}/senha", contaUm.id())
                        .header("Authorization", bearer(adminToken))
                        .contentType("application/json")
                        .content("{\"senha\":\"curta\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/usuarios/{id}", contaDois.id())
                        .header("Authorization", bearer(adminToken))
                        .contentType("application/json")
                        .content("{\"email\":\"PRIMEIRO@EMAIL.COM\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(put("/usuarios/{id}/senha", 999_999L)
                        .header("Authorization", bearer(adminToken))
                        .contentType("application/json")
                        .content("{\"senha\":\"SenhaValida123\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejeitaTokenSemClaimDeVersao() throws Exception {
        Instant agora = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("integrador-api")
                .subject(admin.id().toString())
                .issuedAt(agora)
                .expiresAt(agora.plus(Duration.ofHours(1)))
                .claim("email", ADMIN_EMAIL)
                .claim("pessoaId", admin.pessoaId())
                .claim("roles", List.of("ADMIN"))
                .build();
        String semVersao = jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();

        mockMvc.perform(get("/auth/me").header("Authorization", bearer(semVersao)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void motoristaEnxergaSomenteAsPropriasViagens() throws Exception {
        Motorista autenticado = salvarMotorista("Motorista Autenticado");
        usuarioService.criarUsuario(new CriarUsuarioRequest(
                autenticado.getId(), "proprio@email.com", "SenhaMotorista123"));
        Motorista outro = salvarMotorista("Outro Motorista");

        Viagem propria = new Viagem();
        propria.setData(LocalDate.of(2026, 8, 31));
        propria.setMotorista(autenticado);
        viagemRepository.save(propria);

        Viagem alheia = new Viagem();
        alheia.setData(LocalDate.of(2026, 9, 1));
        alheia.setMotorista(outro);
        viagemRepository.save(alheia);

        mockMvc.perform(get("/viagem/minhas")
                        .header("Authorization", bearer(token("proprio@email.com", "SenhaMotorista123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].motoristaId").value(autenticado.getId()));
    }

    @Test
    void rejeitaTokenExpiradoEAdulterado() throws Exception {
        Instant agora = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("integrador-api")
                .subject(admin.id().toString())
                .issuedAt(agora.minus(Duration.ofHours(3)))
                .expiresAt(agora.minus(Duration.ofHours(1)))
                .claim("email", ADMIN_EMAIL)
                .claim("pessoaId", admin.pessoaId())
                .claim("roles", List.of("ADMIN"))
                .build();
        String expirado = jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
        String valido = tokenAdmin();
        int inicioAssinatura = valido.lastIndexOf('.') + 1;
        char primeiroCaractere = valido.charAt(inicioAssinatura);
        String adulterado = valido.substring(0, inicioAssinatura)
                + (primeiroCaractere == 'A' ? "B" : "A")
                + valido.substring(inicioAssinatura + 1);

        mockMvc.perform(get("/auth/me").header("Authorization", bearer(expirado)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/auth/me").header("Authorization", bearer(adulterado)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void corsPermiteSomenteOrigemDoAngular() throws Exception {
        mockMvc.perform(options("/auth/login")
                        .header("Origin", "http://localhost:4200")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type,authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"));
    }

    private String tokenAdmin() {
        return token(ADMIN_EMAIL, ADMIN_PASSWORD);
    }

    private String token(String email, String senha) {
        LoginResponse response = authService.login(new LoginRequest(email, senha));
        return response.accessToken();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private Aluno salvarAluno(String nome) {
        Aluno aluno = new Aluno();
        aluno.setNome(nome);
        aluno.setTipoPessoa(TipoPessoa.ALUNO);
        return pessoaRepository.save(aluno);
    }

    private Motorista salvarMotorista(String nome) {
        Motorista motorista = new Motorista();
        motorista.setNome(nome);
        motorista.setTipoPessoa(TipoPessoa.MOTORISTA);
        return pessoaRepository.save(motorista);
    }
}
