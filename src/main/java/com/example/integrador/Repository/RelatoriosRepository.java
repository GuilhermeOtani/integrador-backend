package com.example.integrador.Repository;

import com.example.integrador.Model.*;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import com.example.integrador.Enum.StatusFinanceiro;

@Repository
public class RelatoriosRepository {
    private final EntityManager em;
    public RelatoriosRepository(EntityManager em) { this.em = em; }
    public List<ContaReceber> vencidas(LocalDate hoje, Long alunoId, LocalDate inicio, LocalDate fim) {
        String jpql = """
                select c from ContaReceber c join fetch c.aluno a
                where c.dataVencimento < :hoje and (c.status <> :pago or c.status is null or
                    (c.valor > (select coalesce(sum(r.valor),0) from Recebimento r where r.contaReceber = c)
                    and exists(select r.id from Recebimento r where r.contaReceber = c)))
                """;
        // Fragmentos fixos, valores parametrizados. Evita parâmetros temporais sem
        // tipo no PostgreSQL quando uma das pontas do período não foi informada.
        if (alunoId != null) jpql += " and a.id = :aluno";
        if (inicio != null) jpql += " and c.dataVencimento >= :inicio";
        if (fim != null) jpql += " and c.dataVencimento <= :fim";
        var query = em.createQuery(jpql + " order by a.nome asc nulls last, a.id, c.dataVencimento, c.id", ContaReceber.class)
                .setParameter("hoje", hoje).setParameter("pago", StatusFinanceiro.PAGO);
        if (alunoId != null) query.setParameter("aluno", alunoId);
        if (inicio != null) query.setParameter("inicio", inicio);
        if (fim != null) query.setParameter("fim", fim);
        return query.getResultList();
    }
    public List<Recebimento> recebimentos(LocalDate inicio, LocalDate fim) {
        return em.createQuery("select r from Recebimento r join fetch r.contaReceber c join fetch c.aluno where r.dataRecebimento >= :inicio and r.dataRecebimento <= :fim", Recebimento.class)
                .setParameter("inicio", inicio).setParameter("fim", fim).getResultList();
    }
    public List<Pagamento> pagamentos(LocalDate inicio, LocalDate fim) {
        return em.createQuery("select p from Pagamento p left join fetch p.contaPagar c left join fetch c.motorista where p.dataPagamento >= :inicio and p.dataPagamento <= :fim", Pagamento.class)
                .setParameter("inicio", inicio).setParameter("fim", fim).getResultList();
    }
    public BigDecimal saldoAnterior(LocalDate base, LocalDate inicio) {
        BigDecimal entradas = em.createQuery("select coalesce(sum(r.valor),0) from Recebimento r where r.dataRecebimento >= :base and r.dataRecebimento < :inicio", BigDecimal.class)
                .setParameter("base", base).setParameter("inicio", inicio).getSingleResult();
        BigDecimal saidas = em.createQuery("select coalesce(sum(p.valor),0) from Pagamento p where p.dataPagamento >= :base and p.dataPagamento < :inicio", BigDecimal.class)
                .setParameter("base", base).setParameter("inicio", inicio).getSingleResult();
        return entradas.subtract(saidas);
    }
    public List<ContaPagar> contasMotoristas(Long motoristaId, LocalDate competencia) {
        return em.createQuery("select c from ContaPagar c join fetch c.motorista m where type(m) = Motorista and (:id is null or m.id = :id) and (c.competencia = :mes or c.competencia is null) order by m.nome asc nulls last, m.id, c.dataVencimento asc nulls last, c.id", ContaPagar.class)
                .setParameter("id", motoristaId).setParameter("mes", competencia).getResultList();
    }
    public List<Long> cobrancasSemVencimento(Long alunoId) {
        return em.createQuery("select c.id from ContaReceber c where c.dataVencimento is null and (c.status <> :pago or c.status is null) and (:id is null or c.aluno.id = :id) order by c.id", Long.class)
                .setParameter("pago", StatusFinanceiro.PAGO).setParameter("id", alunoId).getResultList();
    }
    public List<Long> pagamentosSemData() {
        return em.createQuery("select p.id from Pagamento p where p.dataPagamento is null order by p.id", Long.class).getResultList();
    }
    public List<Long> recebimentosSemData() {
        return em.createQuery("select r.id from Recebimento r where r.dataRecebimento is null order by r.id", Long.class).getResultList();
    }
    public List<Long> quitacoesReceberSemComprovante() {
        return em.createQuery("select c.id from ContaReceber c where c.status = :pago and not exists(select r.id from Recebimento r where r.contaReceber = c) order by c.id", Long.class)
                .setParameter("pago", StatusFinanceiro.PAGO).getResultList();
    }
    public List<Long> quitacoesSemComprovante() {
        return em.createQuery("select c.id from ContaPagar c where c.status = :pago and not exists(select p.id from Pagamento p where p.contaPagar = c) order by c.id", Long.class)
                .setParameter("pago", StatusFinanceiro.PAGO).getResultList();
    }
}
