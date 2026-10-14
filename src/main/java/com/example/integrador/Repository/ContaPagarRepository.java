package com.example.integrador.Repository;

import com.example.integrador.Model.Aluno;
import com.example.integrador.Model.ContaPagar;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;

public interface ContaPagarRepository extends JpaRepository<ContaPagar, Long> {
    ContaPagar findTopByMotoristaIdOrderByDataVencimentoDesc(Long motoristaId);
    @Query("select c.motorista.id from ContaPagar c where c.id = :id")
    Optional<Long> findPessoaIdById(Long id);
}
