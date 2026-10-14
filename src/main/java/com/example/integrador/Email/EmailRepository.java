package com.example.integrador.Email;

import com.example.integrador.Enum.TipoPessoa;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public class EmailRepository {
    private final EntityManager em;
    public EmailRepository(EntityManager em) { this.em = em; }

    /** Serializa criação de lotes e reserva global. Nunca segurar este lock durante SMTP. */
    public EmailConfiguracao bloquearConfiguracao() {
        return em.find(EmailConfiguracao.class, 1L, LockModeType.PESSIMISTIC_WRITE);
    }
    public EmailConfiguracao configuracao() { return em.find(EmailConfiguracao.class, 1L); }
    public void persistir(Object entidade) { em.persist(entidade); }
    public EmailEntrega entrega(UUID id) { return em.find(EmailEntrega.class, id); }
    public List<EmailDto.Destinatario> associados() {
        return em.createQuery("""
                select new com.example.integrador.Email.EmailDto$Destinatario(u.id, u.email)
                from Usuario u where u.ativo = true and u.tipoPessoa in :papeis
                and not exists (select a.id from Aluno a where a.id = u.pessoa.id and a.arquivado = true)
                order by u.id
                """, EmailDto.Destinatario.class)
                .setParameter("papeis", List.of(TipoPessoa.ALUNO, TipoPessoa.MOTORISTA)).getResultList();
    }
    public List<EmailEntrega> entregas(UUID lote) {
        return em.createQuery("select e from EmailEntrega e where e.lote.id = :lote order by e.email, e.id", EmailEntrega.class)
                .setParameter("lote", lote).getResultList();
    }
    public List<Object[]> totais(UUID lote) {
        return em.createQuery("select e.estado, count(e) from EmailEntrega e where e.lote.id = :lote group by e.estado", Object[].class)
                .setParameter("lote", lote).getResultList();
    }
    public Integer ultimaRevisao(LocalDate dia, String email) {
        return em.createQuery("select max(e.revisao) from EmailEntrega e where e.dataReferencia = :dia and e.email = :email", Integer.class)
                .setParameter("dia", dia).setParameter("email", email).getSingleResult();
    }
    public boolean existeAutomatico(LocalDate dia) {
        return em.createQuery("select count(l) from EmailLote l where l.diaAutomatico = :dia", Long.class)
                .setParameter("dia", dia).getSingleResult() > 0;
    }
    public List<EmailEntrega> pendentes(Instant agora) {
        return em.createQuery("""
                select e from EmailEntrega e join fetch e.lote
                where e.estado = :estado and e.proximaTentativa <= :agora
                order by e.proximaTentativa, e.id
                """, EmailEntrega.class).setParameter("estado", EmailDto.Estado.PENDENTE)
                .setParameter("agora", agora).setMaxResults(1).getResultList();
    }
    public void recuperarReservas(Instant limite) {
        em.createQuery("""
                update EmailEntrega e set e.estado = :incerta, e.erro = 'PROCESSAMENTO_INTERROMPIDO'
                where e.estado = :processando and e.reservadoEm < :limite
                """).setParameter("incerta", EmailDto.Estado.INCERTA)
                .setParameter("processando", EmailDto.Estado.PROCESSANDO).setParameter("limite", limite).executeUpdate();
    }
    public void expirar(LocalDate hoje, boolean depoisDas18) {
        em.createQuery("""
                update EmailEntrega e set e.estado = :expirada, e.erro = 'JANELA_ENCERRADA'
                where e.estado = :pendente and (e.dataReferencia < :hoje
                or (:encerrado = true and e.dataReferencia = :hoje
                    and e.lote.origem = :automatica))
                """).setParameter("expirada", EmailDto.Estado.EXPIRADA)
                .setParameter("pendente", EmailDto.Estado.PENDENTE).setParameter("hoje", hoje)
                .setParameter("encerrado", depoisDas18).setParameter("automatica", EmailDto.Origem.AUTOMATICA).executeUpdate();
    }
}
