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
}
