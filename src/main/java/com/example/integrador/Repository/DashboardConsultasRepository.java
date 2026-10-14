package com.example.integrador.Repository;

import com.example.integrador.DTO.ViagemDashboardBase;
import com.example.integrador.Enum.DiaSemana;
import com.example.integrador.Enum.NivelUrgencia;
import com.example.integrador.Enum.StatusChamado;
import com.example.integrador.Enum.StatusFinanceiro;
import com.example.integrador.Model.Rota;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class DashboardConsultasRepository {
    private final EntityManager em;
    private static final String VIAGEM = """
            select new com.example.integrador.DTO.ViagemDashboardBase(
                v.id, g.id, g.diaSemana, g.descricao, r.id,
                o.id, o.numeroIdentificacao, o.placa, o.modelo, o.capacidade, o.statusOnibus,
                m.id, m.nome)
            from Viagem v left join v.gradeDiaria g left join v.rota r
            left join v.onibus o left join v.motorista m
            """;

    public enum TipoConta {
        RECEBER("ContaReceber", "aluno"), PAGAR("ContaPagar", "motorista");
        private final String entidade;
        private final String pessoa;
        TipoConta(String entidade, String pessoa) { this.entidade = entidade; this.pessoa = pessoa; }
    }

    public DashboardConsultasRepository(EntityManager em) { this.em = em; }

    public List<ViagemDashboardBase> viagensAluno(Long faculdadeId) {
        if (faculdadeId == null) return List.of();
        return em.createQuery(VIAGEM + """
                where v.id in (select outra.id from Viagem outra join outra.faculdades f where f.id = :faculdade)
                order by v.id
                """, ViagemDashboardBase.class).setParameter("faculdade", faculdadeId).getResultList();
    }

    public List<ViagemDashboardBase> viagensMotorista(Long pessoaId) {
        return em.createQuery(VIAGEM + "where m.id = :pessoa order by v.id", ViagemDashboardBase.class)
                .setParameter("pessoa", pessoaId).getResultList();
    }

    public List<Object[]> faculdadesDasViagens(Collection<Long> ids) {
        if (ids.isEmpty()) return List.of();
        return em.createQuery("select v.id, f.id, f.nome, f.endereco from Viagem v join v.faculdades f where v.id in :ids order by f.id", Object[].class)
                .setParameter("ids", ids).getResultList();
    }

    public List<Rota> rotas(Long faculdadeId, Collection<Long> ids) {
        if (faculdadeId == null && ids.isEmpty()) return List.of();
        String condicao = ids.isEmpty() ? "f.id = :faculdade"
                : faculdadeId == null ? "r.id in :ids" : "(f.id = :faculdade or r.id in :ids)";
        var query = em.createQuery("select r from Rota r left join r.faculdade f where " + condicao + " order by r.nome, r.id", Rota.class);
        if (faculdadeId != null) query.setParameter("faculdade", faculdadeId);
        if (!ids.isEmpty()) query.setParameter("ids", ids);
        return query.getResultList();
    }

    public List<Object[]> pontosDaColecao(Collection<Long> ids) {
        if (ids.isEmpty()) return List.of();
        return em.createQuery("select r.id, p.id, p.descricao, p.ordemParada from Rota r join r.pontosEmbarque p where r.id in :ids", Object[].class)
                .setParameter("ids", ids).getResultList();
    }

    public List<Object[]> pontosDaReferencia(Collection<Long> ids) {
        if (ids.isEmpty()) return List.of();
        return em.createQuery("select p.rota.id, p.id, p.descricao, p.ordemParada from PontoEmbarque p where p.rota.id in :ids", Object[].class)
                .setParameter("ids", ids).getResultList();
    }

    public Object[] resumoContas(TipoConta tipo, LocalDate hoje) {
        // Não há joins nesta agregação: cada conta é contada/somada uma única vez.
        return em.createQuery("""
                select count(c), coalesce(sum(c.valor), 0),
                    coalesce(sum(case when c.dataVencimento < :hoje then 1 else 0 end), 0),
                    coalesce(sum(case when c.dataVencimento < :hoje then c.valor else 0 end), 0),
                    coalesce(sum(case when c.dataVencimento >= :hoje and c.dataVencimento < :fim then 1 else 0 end), 0),
                    coalesce(sum(case when c.dataVencimento >= :hoje and c.dataVencimento < :fim then c.valor else 0 end), 0)
                from %s c where c.status <> :pago or c.status is null
                """.formatted(tipo.entidade), Object[].class)
                .setParameter("hoje", hoje).setParameter("fim", hoje.plusDays(7))
                .setParameter("pago", StatusFinanceiro.PAGO).getSingleResult();
    }

    public List<Object[]> pendencias(TipoConta tipo) {
        return em.createQuery("select c.id, c.descricao, c.valor, c.dataVencimento, c.status, p.nome from "
                        + tipo.entidade + " c left join c." + tipo.pessoa + " p where c.status <> :pago or c.status is null"
                        + " order by c.dataVencimento asc nulls last, c.id", Object[].class)
                .setParameter("pago", StatusFinanceiro.PAGO).setMaxResults(5).getResultList();
    }

    public Map<YearMonth, BigDecimal> recebimentos(LocalDate inicio, LocalDate fim) {
        return movimentos("Recebimento", "dataRecebimento", inicio, fim);
    }

    public Map<YearMonth, BigDecimal> pagamentos(LocalDate inicio, LocalDate fim) {
        return movimentos("Pagamento", "dataPagamento", inicio, fim);
    }

    private Map<YearMonth, BigDecimal> movimentos(String entidade, String data, LocalDate inicio, LocalDate fim) {
        // Identificadores vêm exclusivamente das duas chamadas internas acima, nunca do cliente.
        List<Object[]> rows = em.createQuery("select year(m." + data + "), month(m." + data + "), sum(m.valor) from "
                        + entidade + " m where m." + data + " >= :inicio and m." + data + " < :fim"
                        + " group by year(m." + data + "), month(m." + data + ")", Object[].class)
                .setParameter("inicio", inicio).setParameter("fim", fim).getResultList();
        Map<YearMonth, BigDecimal> resultado = new HashMap<>();
        for (Object[] row : rows) {
            resultado.put(YearMonth.of(((Number) row[0]).intValue(), ((Number) row[1]).intValue()), decimal(row[2]));
        }
        return resultado;
    }

    public List<Object[]> alunosPorStatus() {
        return em.createQuery("select a.statusMatricula, count(a) from Aluno a group by a.statusMatricula", Object[].class).getResultList();
    }

    public List<Object[]> onibusPorStatus() {
        return em.createQuery("select o.statusOnibus, count(o) from Onibus o group by o.statusOnibus", Object[].class).getResultList();
    }

    public List<Object[]> chamadosPorStatus() {
        return em.createQuery("select c.status, count(c), sum(case when c.urgencia = :alta then 1 else 0 end) from ChamadoManutencao c group by c.status", Object[].class)
                .setParameter("alta", NivelUrgencia.ALTA).getResultList();
    }

    public long viagensHoje(DiaSemana dia) {
        if (dia == null) return 0;
        return em.createQuery("select count(v) from Viagem v join v.gradeDiaria g where g.diaSemana = :dia", Long.class)
                .setParameter("dia", dia).getSingleResult();
    }

    public long viagensSemProgramacao() {
        return em.createQuery("select count(v) from Viagem v left join v.gradeDiaria g where g is null or g.diaSemana is null", Long.class).getSingleResult();
    }

    public List<Object[]> chamadosPendentes() {
        return em.createQuery("""
                select c.id, c.descricao, c.dataAbertura, c.urgencia, c.status,
                       o.id, o.numeroIdentificacao, o.placa, p.nome
                from ChamadoManutencao c join c.onibus o join c.solicitante p
                where c.status in :status
                order by case when c.urgencia = :alta then 0 when c.urgencia = :media then 1 else 2 end,
                         c.dataAbertura, c.id
                """, Object[].class).setParameter("status", List.of(StatusChamado.ABERTO, StatusChamado.EM_ANDAMENTO))
                .setParameter("alta", NivelUrgencia.ALTA).setParameter("media", NivelUrgencia.MEDIA)
                .setMaxResults(5).getResultList();
    }

    public static BigDecimal decimal(Object valor) {
        return valor == null ? BigDecimal.ZERO : valor instanceof BigDecimal decimal ? decimal : new BigDecimal(valor.toString());
    }
}
