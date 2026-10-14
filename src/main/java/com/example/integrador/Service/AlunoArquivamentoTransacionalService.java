package com.example.integrador.Service;

import com.example.integrador.DTO.SituacaoExclusaoAlunoResponse;
import com.example.integrador.DTO.SituacaoExclusaoAlunoResponse.Impedimento;
import com.example.integrador.Exception.ExclusaoAlunoBloqueadaException;
import com.example.integrador.Exception.RecursoNaoEncontradoException;
import com.example.integrador.Model.Aluno;
import com.example.integrador.Repository.AlunoVinculosRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;

/** Unidade de escrita separada da fachada: erros de integridade saem após rollback. */
@Service
@PreAuthorize("hasRole('ADMIN')")
public class AlunoArquivamentoTransacionalService {
    private final PessoaBloqueioService bloqueios;
    private final AlunoVinculosRepository vinculos;
    private final EntityManager em;
    public AlunoArquivamentoTransacionalService(PessoaBloqueioService bloqueios,
                                                AlunoVinculosRepository vinculos, EntityManager em) {
        this.bloqueios = bloqueios;
        this.vinculos = vinculos;
        this.em = em;
    }
    @Transactional
    public Aluno arquivar(Long id, boolean arquivado) {
        Aluno aluno = bloqueios.bloquearAluno(id);
        boolean transicao = aluno.isArquivado() != arquivado;
        var usuario = bloqueios.usuarioDaPessoaBloqueada(id);
        boolean revogar = usuario != null && ((arquivado && (transicao || usuario.isAtivo()))
                || (!arquivado && transicao && usuario.isAtivo()));
        aluno.setArquivado(arquivado);
        if (revogar) {
            usuario.setAtivo(false);
            usuario.setTokenVersion(usuario.getTokenVersion() + 1);
        }
        return aluno;
    }

    @Transactional(readOnly = true)
    public SituacaoExclusaoAlunoResponse situacao(Long id) {
        Aluno aluno = em.find(Aluno.class, id);
        if (aluno == null) throw new RecursoNaoEncontradoException("Aluno não encontrado.");
        return situacao(aluno);
    }

    @Transactional
    public void excluir(Long id) {
        Aluno aluno = bloqueios.bloquearAluno(id);
        var situacao = situacao(aluno);
        if (!situacao.arquivado()) throw new ExclusaoAlunoBloqueadaException("ALUNO_NAO_ARQUIVADO",
                "Arquive o aluno antes de tentar excluir definitivamente.", situacao.vinculos());
        if (!situacao.podeExcluir()) throw new ExclusaoAlunoBloqueadaException("ALUNO_POSSUI_VINCULOS",
                "O aluno possui registros vinculados. Mantenha-o arquivado para preservar o histórico.",
                situacao.vinculos());
        var usuario = bloqueios.usuarioDaPessoaBloqueada(id);
        if (usuario != null) {
            em.remove(usuario);
            em.flush(); // Garante a ordem mesmo com exclusão da herança SINGLE_TABLE.
        }
        em.remove(aluno);
        em.flush();
    }

    private SituacaoExclusaoAlunoResponse situacao(Aluno aluno) {
        var contagens = vinculos.contar(aluno.getId());
        var impedimentos = new ArrayList<Impedimento>();
        if (!aluno.isArquivado()) impedimentos.add(new Impedimento("ALUNO_NAO_ARQUIVADO", "Arquive o aluno antes da exclusão definitiva."));
        if (contagens.contasReceber() > 0) impedimentos.add(new Impedimento("CONTAS_RECEBER", "O aluno possui " + contagens.contasReceber() + " contas a receber vinculadas."));
        if (contagens.contasPagar() > 0) impedimentos.add(new Impedimento("CONTAS_PAGAR", "O aluno possui " + contagens.contasPagar() + " contas a pagar vinculadas."));
        if (contagens.chamados() > 0) impedimentos.add(new Impedimento("CHAMADOS", "O aluno possui " + contagens.chamados() + " chamados de manutenção vinculados."));
        if (contagens.viagens() > 0) impedimentos.add(new Impedimento("VIAGENS", "O aluno possui " + contagens.viagens() + " viagens vinculadas."));
        return new SituacaoExclusaoAlunoResponse(aluno.getId(), aluno.isArquivado(), impedimentos.isEmpty(),
                vinculos.possuiAcesso(aluno.getId()), contagens, java.util.List.copyOf(impedimentos));
    }
}
