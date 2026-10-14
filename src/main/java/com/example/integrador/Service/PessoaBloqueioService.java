package com.example.integrador.Service;

import com.example.integrador.Exception.ConflitoException;
import com.example.integrador.Exception.RecursoNaoEncontradoException;
import com.example.integrador.Model.Aluno;
import com.example.integrador.Model.Pessoa;
import com.example.integrador.Model.Usuario;
import com.example.integrador.Enum.TipoPessoa;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.Collection;
import java.util.TreeSet;

/** Sincronização interna. Fachadas aplicam autorização; nenhuma chamada externa durante estes locks. */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class PessoaBloqueioService {
    private final EntityManager em;
    public PessoaBloqueioService(EntityManager em) { this.em = em; }

    public Pessoa bloquearPessoa(Long id) {
        Pessoa pessoa = em.find(Pessoa.class, id);
        if (pessoa == null) throw new RecursoNaoEncontradoException("Pessoa não encontrada.");
        pessoa = (Pessoa) Hibernate.unproxy(pessoa);
        // Refresh corrige entidades carregadas antes de outra transação concluir o arquivamento.
        // Se o lock já é desta transação, preservar as alterações locais legítimas.
        if (em.getLockMode(pessoa) != LockModeType.PESSIMISTIC_WRITE) {
            try { em.refresh(pessoa, LockModeType.PESSIMISTIC_WRITE); }
            catch (jakarta.persistence.EntityNotFoundException exception) {
                throw new RecursoNaoEncontradoException("Pessoa não encontrada.");
            }
        }
        return pessoa;
    }

    public Aluno bloquearAluno(Long id) {
        Pessoa pessoa = bloquearPessoa(id);
        if (!(pessoa instanceof Aluno aluno)) throw new RecursoNaoEncontradoException("Aluno não encontrado.");
        return aluno;
    }

    public void exigirNaoArquivado(Pessoa pessoa) {
        if (pessoa instanceof Aluno aluno && aluno.isArquivado())
            throw new ConflitoException("O aluno está arquivado. Restaure o cadastro antes desta operação.");
    }

    public void bloquearPessoasEmOrdem(Collection<Long> ids) {
        for (Long id : new TreeSet<>(ids)) bloquearPessoa(id);
    }

    public Usuario bloquearUsuario(Long id) {
        var pessoas = em.createQuery("select u.pessoa.id from Usuario u where u.id = :id", Long.class)
                .setParameter("id", id).getResultList();
        if (pessoas.isEmpty()) throw new RecursoNaoEncontradoException("Usuário não encontrado.");
        bloquearPessoa(pessoas.getFirst());
        Usuario usuario = em.find(Usuario.class, id);
        if (usuario == null) throw new RecursoNaoEncontradoException("Usuário não encontrado.");
        // ADMIN mantém o protocolo existente que bloqueia o conjunto de administradores ativos.
        // Não adquirir lock individual antes daquele conjunto e introduzir uma inversão.
        if (usuario.getTipoPessoa() == TipoPessoa.ADMIN) {
            em.refresh(usuario);
            return usuario;
        }
        if (em.getLockMode(usuario) != LockModeType.PESSIMISTIC_WRITE)
            em.refresh(usuario, LockModeType.PESSIMISTIC_WRITE);
        return usuario;
    }

    public Usuario usuarioDaPessoaBloqueada(Long pessoaId) {
        var ids = em.createQuery("select u.id from Usuario u where u.pessoa.id = :id", Long.class)
                .setParameter("id", pessoaId).getResultList();
        return ids.isEmpty() ? null : bloquearUsuario(ids.getFirst());
    }
}
