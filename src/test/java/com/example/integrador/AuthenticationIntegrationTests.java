package com.example.integrador;

import com.example.integrador.DTO.CriarAdminRequest;
import com.example.integrador.DTO.CriarUsuarioRequest;
import com.example.integrador.DTO.LoginRequest;
import com.example.integrador.DTO.LoginResponse;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
                .andExpect(jsonPath("$.usuario.senha").doesNotExist());

        Jwt jwt = jwtDecoder.decode(tokenAdmin());
        assertThat(jwt.getSubject()).isEqualTo(admin.id().toString());
        assertThat(jwt.getClaimAsString("email")).isEqualTo(ADMIN_EMAIL);
        assertThat(jwt.<Long>getClaim("pessoaId")).isEqualTo(admin.pessoaId());
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("ADMIN");
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
                .andExpect(jsonPath("$[0].senha").doesNotExist());
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
        String adulterado = valido.substring(0, valido.length() - 1)
                + (valido.endsWith("A") ? "B" : "A");

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
