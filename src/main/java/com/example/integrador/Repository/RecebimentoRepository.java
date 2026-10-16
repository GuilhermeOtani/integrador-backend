package com.example.integrador.Repository;

import com.example.integrador.Model.Recebimento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.List;
import java.util.Collection;
import java.math.BigDecimal;

public interface RecebimentoRepository extends JpaRepository<Recebimento, Long> {
    Optional<Recebimento> findFirstByContaReceberIdOrderByDataRecebimentoDescIdDesc(Long contaReceberId);
    List<Recebimento> findAllByContaReceberIdOrderByDataRecebimentoDescIdDesc(Long contaReceberId);
    Optional<Recebimento> findByChaveIdempotencia(String chave);

    boolean existsByContaReceberId(Long contaReceberId);

    @Query("select coalesce(sum(r.valor),0) from Recebimento r where r.contaReceber.id = :id")
    BigDecimal totalRecebido(Long id);

    @Query("select r.contaReceber.id, coalesce(sum(r.valor),0), count(r) from Recebimento r where r.contaReceber.id in :ids group by r.contaReceber.id")
    List<Object[]> totaisPorContas(Collection<Long> ids);
}
