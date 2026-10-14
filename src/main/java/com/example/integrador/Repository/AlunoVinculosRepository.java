package com.example.integrador.Repository;

import com.example.integrador.DTO.SituacaoExclusaoAlunoResponse.Vinculos;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

@Repository
public class AlunoVinculosRepository {
    private final EntityManager em;
    public AlunoVinculosRepository(EntityManager em) { this.em = em; }
    public Vinculos contar(Long pessoaId) {
        // Referências diretas também contam dados legados com subtipo inconsistente.
        return new Vinculos(contar("conta_receber", "aluno_id", pessoaId),
                contar("conta_pagar", "pessoa_id", pessoaId),
                contar("chamado_manutencao", "responsavel_id", pessoaId),
                contar("viagem", "motorista_id", pessoaId));
    }
    public boolean possuiAcesso(Long pessoaId) { return contar("usuario", "pessoa_id", pessoaId) > 0; }
    private long contar(String tabela, String coluna, Long id) {
        // Identificadores exclusivamente internos e fixos; valores sempre parametrizados.
        return ((Number) em.createNativeQuery("select count(*) from " + tabela + " where " + coluna + " = :id")
                .setParameter("id", id).getSingleResult()).longValue();
    }
}
