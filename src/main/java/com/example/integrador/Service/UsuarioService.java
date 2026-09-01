package com.example.integrador.Service;

import com.example.integrador.DTO.CriarAdminRequest;
import com.example.integrador.DTO.CriarUsuarioRequest;
import com.example.integrador.DTO.PessoaDisponivelResponse;
import com.example.integrador.DTO.UsuarioResponse;
import com.example.integrador.Enum.TipoPessoa;
import com.example.integrador.Exception.ConflitoException;
import com.example.integrador.Exception.RecursoNaoEncontradoException;
import com.example.integrador.Exception.RegraNegocioException;
import com.example.integrador.Model.Aluno;
import com.example.integrador.Model.Motorista;
import com.example.integrador.Model.Pessoa;
import com.example.integrador.Model.Usuario;
import com.example.integrador.Repository.PessoaRepository;
import com.example.integrador.Repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
public class UsuarioService {
    private final UsuarioRepository usuarioRepository;
    private final PessoaRepository pessoaRepository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(UsuarioRepository usuarioRepository,
                          PessoaRepository pessoaRepository,
                          PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.pessoaRepository = pessoaRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<UsuarioResponse> listarTodos() {
        return usuarioRepository.findAllByOrderByEmailAsc().stream()
                .map(UsuarioResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PessoaDisponivelResponse> listarPessoasSemUsuario() {
        return pessoaRepository.findAllByOrderByNomeAsc().stream()
                .filter(pessoa -> pessoa instanceof Aluno || pessoa instanceof Motorista)
                .filter(pessoa -> !usuarioRepository.existsByPessoaId(pessoa.getId()))
                .map(pessoa -> new PessoaDisponivelResponse(
                        pessoa.getId(), pessoa.getNome(), papelDaPessoa(pessoa)))
                .toList();
    }

    @Transactional
    public UsuarioResponse criarUsuario(CriarUsuarioRequest request) {
        Pessoa pessoa = pessoaRepository.findById(request.pessoaId())
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Pessoa de ID " + request.pessoaId() + " não encontrada"));

        if (!(pessoa instanceof Aluno) && !(pessoa instanceof Motorista)) {
            throw new RegraNegocioException("Somente alunos e motoristas podem receber uma conta por este endpoint");
        }

        return UsuarioResponse.fromEntity(
                criarConta(pessoa, request.email(), request.senha(), papelDaPessoa(pessoa)));
    }

    @Transactional
    public UsuarioResponse criarAdmin(CriarAdminRequest request) {
        validarEmailDisponivel(request.email());

        Pessoa pessoa = new Pessoa();
        pessoa.setNome(request.nome().trim());
        pessoa.setTipoPessoa(TipoPessoa.ADMIN);
        Pessoa pessoaSalva = pessoaRepository.save(pessoa);

        return UsuarioResponse.fromEntity(
                criarConta(pessoaSalva, request.email(), request.senha(), TipoPessoa.ADMIN));
    }

    @Transactional
    public Usuario criarAdminInicial(String nome, String email, String senha) {
        if (existeAdmin()) {
            throw new ConflitoException("Já existe um administrador cadastrado");
        }

        Pessoa pessoa = new Pessoa();
        pessoa.setNome(nome.trim());
        pessoa.setTipoPessoa(TipoPessoa.ADMIN);
        return criarConta(pessoaRepository.save(pessoa), email, senha, TipoPessoa.ADMIN);
    }

    @Transactional(readOnly = true)
    public boolean existeAdmin() {
        return usuarioRepository.existsByTipoPessoa(TipoPessoa.ADMIN);
    }

    @Transactional(readOnly = true)
    public Usuario buscarEntidadePorId(Long id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Usuário de ID " + id + " não encontrado"));
    }

    private Usuario criarConta(Pessoa pessoa, String email, String senha, TipoPessoa papel) {
        validarEmailDisponivel(email);
        if (usuarioRepository.existsByPessoaId(pessoa.getId())) {
            throw new ConflitoException("A pessoa selecionada já possui uma conta");
        }

        pessoa.setTipoPessoa(papel);
        pessoaRepository.save(pessoa);

        Usuario usuario = new Usuario();
        usuario.setPessoa(pessoa);
        usuario.setEmail(normalizarEmail(email));
        usuario.setSenha(passwordEncoder.encode(senha));
        usuario.setTipoPessoa(papel);
        return usuarioRepository.save(usuario);
    }

    private void validarEmailDisponivel(String email) {
        if (usuarioRepository.existsByEmailIgnoreCase(normalizarEmail(email))) {
            throw new ConflitoException("Já existe uma conta com este e-mail");
        }
    }

    private TipoPessoa papelDaPessoa(Pessoa pessoa) {
        if (pessoa instanceof Aluno) {
            return TipoPessoa.ALUNO;
        }
        if (pessoa instanceof Motorista) {
            return TipoPessoa.MOTORISTA;
        }
        return TipoPessoa.ADMIN;
    }

    private String normalizarEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
