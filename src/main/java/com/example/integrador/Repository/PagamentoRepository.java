package com.example.integrador.Repository;

import com.example.integrador.Model.Pagamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;
import java.math.BigDecimal;

public interface PagamentoRepository extends JpaRepository<Pagamento, Long> {
    Pagamento findFirstByContaPagarIdOrderByDataPagamentoDescIdDesc(Long contaId);
    List<Pagamento> findAllByContaPagarIdOrderByDataPagamentoDescIdDesc(Long contaId);
    Optional<Pagamento> findByChaveIdempotencia(String chave);
    boolean existsByContaPagarId(Long contaId);
    @Query("select coalesce(sum(p.valor),0) from Pagamento p where p.contaPagar.id = :contaId")
    BigDecimal totalPago(Long contaId);
    @Query("select p.contaPagar.id, coalesce(sum(p.valor),0), count(p) from Pagamento p where p.contaPagar.id in :ids group by p.contaPagar.id")
    List<Object[]> totaisPorContas(java.util.Collection<Long> ids);
}
