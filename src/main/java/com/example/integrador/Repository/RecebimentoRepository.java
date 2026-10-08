package com.example.integrador.Repository;

import com.example.integrador.Model.Recebimento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RecebimentoRepository extends JpaRepository<Recebimento, Long> {
    Optional<Recebimento> findByContaReceberId(Long contaReceberId);

    boolean existsByContaReceberId(Long contaReceberId);
}
