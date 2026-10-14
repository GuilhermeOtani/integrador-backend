package com.example.integrador.Service;

import com.example.integrador.Exception.ConflitoException;
import com.example.integrador.Model.Aluno;
import jakarta.persistence.EntityManager;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@PreAuthorize("hasRole('ADMIN')")
public class PessoaExclusaoTransacionalService {
    private final PessoaBloqueioService bloqueios;
    private final ProtecaoHistoricoManutencaoService historico;
    private final EntityManager em;
    public PessoaExclusaoTransacionalService(PessoaBloqueioService bloqueios,
                                            ProtecaoHistoricoManutencaoService historico, EntityManager em) {
        this.bloqueios = bloqueios; this.historico = historico; this.em = em;
    }
    @Transactional
    public void excluirNaoAluno(Long id) {
        var pessoa = bloqueios.bloquearPessoa(id);
        if (pessoa instanceof Aluno) throw new ConflitoException("Utilize a exclusão protegida de aluno.");
        historico.verificarPessoa(id);
        em.remove(pessoa);
    }
}
