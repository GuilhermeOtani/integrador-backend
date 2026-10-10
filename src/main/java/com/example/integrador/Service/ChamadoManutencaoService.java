package com.example.integrador.Service;

import com.example.integrador.DTO.*;
import com.example.integrador.Enum.NivelUrgencia;
import com.example.integrador.Enum.StatusChamado;
import com.example.integrador.Enum.TipoPessoa;
import com.example.integrador.Exception.RecursoNaoEncontradoException;
import com.example.integrador.Exception.RegraNegocioException;
import com.example.integrador.Model.ChamadoManutencao;
import com.example.integrador.Model.Usuario;
import com.example.integrador.Repository.ChamadoManutencaoRepository;
import com.example.integrador.Repository.OnibusRepository;
import com.example.integrador.Repository.UsuarioRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class ChamadoManutencaoService {
    private final ChamadoManutencaoRepository repository;
    private final OnibusRepository onibusRepository;
    private final UsuarioRepository usuarioRepository;

    public ChamadoManutencaoService(ChamadoManutencaoRepository repository,
                                   OnibusRepository onibusRepository,
                                   UsuarioRepository usuarioRepository) {
        this.repository = repository;
        this.onibusRepository = onibusRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ALUNO', 'MOTORISTA')")
    public ChamadoManutencaoResponse abrir(AbrirChamadoRequest request) {
        Usuario usuario = usuarioAtual();
        var onibus = onibusRepository.findById(request.onibusId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Ônibus não encontrado"));
        ChamadoManutencao chamado = new ChamadoManutencao();
        chamado.setOnibus(onibus);
        chamado.setSolicitante(usuario.getPessoa());
        chamado.setDescricao(request.descricao().trim());
        chamado.setDataAbertura(LocalDate.now());
        chamado.setStatus(StatusChamado.ABERTO);
        chamado.setUrgencia(NivelUrgencia.MEDIA);
        return ChamadoManutencaoResponse.fromEntity(repository.save(chamado));
    }

    @PreAuthorize("hasAnyRole('ALUNO', 'MOTORISTA')")
    public List<ChamadoManutencaoResponse> minhas() {
        return repository.findAllBySolicitanteIdOrderByDataAberturaDescIdDesc(
                        usuarioAtual().getPessoa().getId()).stream()
                .map(ChamadoManutencaoResponse::fromEntity).toList();
    }

    @PreAuthorize("hasRole('ADMIN')")
    public List<ChamadoManutencaoResponse> listar(StatusChamado status, NivelUrgencia urgencia,
                                                 Long onibusId) {
        Specification<ChamadoManutencao> filtro = (root, query, cb) -> {
            var condicoes = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            if (status != null) condicoes.add(cb.equal(root.get("status"), status));
            if (urgencia != null) condicoes.add(cb.equal(root.get("urgencia"), urgencia));
            if (onibusId != null) condicoes.add(cb.equal(root.get("onibus").get("id"), onibusId));
            return cb.and(condicoes.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        return repository.findAll(filtro, Sort.by(Sort.Direction.DESC, "dataAbertura", "id"))
                .stream().map(ChamadoManutencaoResponse::fromEntity).toList();
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'ALUNO', 'MOTORISTA')")
    public ChamadoManutencaoResponse buscar(Long id) {
        ChamadoManutencao chamado = buscarEntidade(id);
        Usuario usuario = usuarioAtual();
        if (usuario.getTipoPessoa() != TipoPessoa.ADMIN
                && !chamado.getSolicitante().getId().equals(usuario.getPessoa().getId())) {
            throw new RecursoNaoEncontradoException("Chamado não encontrado");
        }
        return ChamadoManutencaoResponse.fromEntity(chamado);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'ALUNO', 'MOTORISTA')")
    public List<OnibusChamadoResponse> listarOnibus() {
        return onibusRepository.findAll(Sort.by("numeroIdentificacao", "id")).stream()
                .map(OnibusChamadoResponse::fromEntity).toList();
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public ChamadoManutencaoResponse atender(Long id, AtenderChamadoRequest request) {
        ChamadoManutencao chamado = buscarEntidade(id);
        String observacao = request.observacaoAdmin() == null ? null : request.observacaoAdmin().trim();
        if (request.status() == StatusChamado.RESOLVIDO && (observacao == null || observacao.isBlank())) {
            throw new RegraNegocioException("Informe uma observação para resolver o chamado");
        }
        chamado.setUrgencia(request.urgencia());
        chamado.setStatus(request.status());
        chamado.setObservacaoAdmin(observacao);
        if (request.statusOnibus() != null) {
            chamado.getOnibus().setStatusOnibus(request.statusOnibus());
        }
        return ChamadoManutencaoResponse.fromEntity(chamado);
    }

    private ChamadoManutencao buscarEntidade(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Chamado não encontrado"));
    }

    private Usuario usuarioAtual() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwt)) {
            throw new AuthenticationCredentialsNotFoundException("JWT obrigatório");
        }
        return usuarioRepository.findById(Long.valueOf(jwt.getToken().getSubject()))
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("Conta inexistente"));
    }
}
