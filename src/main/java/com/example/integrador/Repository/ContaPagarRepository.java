package com.example.integrador.Repository;

import com.example.integrador.Model.ContaPagar;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.List;
import java.time.LocalDate;
import com.example.integrador.Enum.ClassificacaoConta;

public interface ContaPagarRepository extends JpaRepository<ContaPagar, Long> {
    ContaPagar findTopByMotoristaIdOrderByDataVencimentoDesc(Long motoristaId);
    @Query("select c from ContaPagar c where c.motorista.id = :id and c.classificacao = :classificacao and c.competencia is not null and c.dataVencimento is not null order by c.competencia desc, c.dataVencimento desc, c.id desc limit 1")
    ContaPagar findFirstByMotoristaIdAndClassificacaoOrderByCompetenciaDescDataVencimentoDescIdDesc(Long id, ClassificacaoConta classificacao);
    boolean existsByMotoristaIdAndClassificacaoAndCompetencia(Long id, ClassificacaoConta classificacao, LocalDate competencia);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ContaPagar c where c.id = :id")
    Optional<ContaPagar> findLockedById(Long id);
    @Query("select c from ContaPagar c left join fetch c.motorista order by c.dataVencimento asc nulls last, c.id")
    List<ContaPagar> listarComPessoa();

    @Query("""
            select c from ContaPagar c left join fetch c.motorista
            where c.status is null or c.status <> com.example.integrador.Enum.StatusFinanceiro.PAGO
                or (c.valor > (select coalesce(sum(p.valor),0) from Pagamento p where p.contaPagar = c)
                    and exists(select p.id from Pagamento p where p.contaPagar = c))
            order by c.dataVencimento asc nulls last, c.id
            """)
    List<ContaPagar> listarPotencialmenteAbertas();
}
