package com.example.integrador.Repository;

import com.example.integrador.Model.Aluno;
import com.example.integrador.Model.ContaReceber;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ContaReceberRepository extends JpaRepository<ContaReceber, Long> {
    ContaReceber findTopByAlunoIdOrderByDataVencimentoDesc(Long alunoId);

    List<ContaReceber> findAllByAlunoIdOrderByDataVencimentoDesc(Long alunoId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select conta from ContaReceber conta where conta.id = :id")
    Optional<ContaReceber> findLockedById(Long id);

    @Query("select c.aluno.id from ContaReceber c where c.id = :id")
    Optional<Long> findAlunoIdById(Long id);

    @Query("select c from ContaReceber c where c.aluno.id = :id and c.dataVencimento is not null order by c.dataVencimento desc, c.id desc limit 1")
    ContaReceber ultimaContaRecorrente(Long id);

    @Query("select c from ContaReceber c join fetch c.aluno order by c.dataVencimento desc nulls last, c.id desc")
    List<ContaReceber> listarComAluno();

    @Query("""
            select c from ContaReceber c join fetch c.aluno
            where c.status is null or c.status <> com.example.integrador.Enum.StatusFinanceiro.PAGO
                or (c.valor > (select coalesce(sum(r.valor),0) from Recebimento r where r.contaReceber = c)
                    and exists(select r.id from Recebimento r where r.contaReceber = c))
            order by c.dataVencimento asc nulls last, c.id
            """)
    List<ContaReceber> listarPotencialmenteAbertas();
}
