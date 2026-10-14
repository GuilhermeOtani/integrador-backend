package com.example.integrador.Whatsapp;

import com.example.integrador.Whatsapp.WhatsappTipos.StatusEntrega;
import com.example.integrador.Whatsapp.WhatsappTipos.StatusAssinatura;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@Repository
public class WhatsappRepos {
    private final EntityManager em;
    public WhatsappRepos(EntityManager em) { this.em = em; }

    public WhatsappConfig bloquearConfiguracao() {
        return em.find(WhatsappConfig.class, 1L, LockModeType.PESSIMISTIC_WRITE);
    }
    public WhatsappAssinatura assinatura(Long usuarioId) {
        return em.createQuery("select a from WhatsappAssinatura a join fetch a.usuario u where u.id = :id", WhatsappAssinatura.class)
                .setParameter("id", usuarioId).getResultStream().findFirst().orElse(null);
    }
    public List<WhatsappAssinatura> porNumero(String numero) {
        return em.createQuery("select a from WhatsappAssinatura a join fetch a.usuario where a.numero = :numero", WhatsappAssinatura.class)
                .setParameter("numero", numero).getResultList();
    }
    public WhatsappAssinatura porCodigo(String hash) {
        return em.createQuery("select a from WhatsappAssinatura a join fetch a.usuario where a.codigoHash = :hash", WhatsappAssinatura.class)
                .setParameter("hash", hash).getResultStream().findFirst().orElse(null);
    }
    public List<WhatsappAssinatura> destinatarios() {
        return em.createQuery("select a from WhatsappAssinatura a join fetch a.usuario u join fetch u.pessoa where a.status = :ativa and u.ativo = true order by u.id", WhatsappAssinatura.class)
                .setParameter("ativa", StatusAssinatura.ATIVA).getResultList();
    }
    public List<WhatsappEntrega> entregas(UUID lote) {
        return em.createQuery("select e from WhatsappEntrega e where e.lote.id = :id order by e.destinatarioOriginalId, e.revisao", WhatsappEntrega.class)
                .setParameter("id", lote).getResultList();
    }
    public List<WhatsappEntrega> anteriores(LocalDate data, Long usuarioId) {
        return em.createQuery("select e from WhatsappEntrega e where e.dataReferencia = :data and e.destinatarioOriginalId = :id order by e.revisao desc", WhatsappEntrega.class)
                .setParameter("data", data).setParameter("id", usuarioId).getResultList();
    }
    public boolean temAutomatico(LocalDate data) {
        return em.createQuery("select count(l) from WhatsappLote l where l.diaAutomatico = :dia", Long.class)
                .setParameter("dia", data).getSingleResult() > 0;
    }
    public List<WhatsappEntrega> pendentes(Instant agora) {
        return em.createQuery("select e from WhatsappEntrega e join fetch e.lote where e.status = :pendente and (e.proximaTentativa is null or e.proximaTentativa <= :agora) order by e.criadoEm, e.id", WhatsappEntrega.class)
                .setParameter("pendente", StatusEntrega.PENDENTE).setParameter("agora", agora).setMaxResults(50).getResultList();
    }
    public List<WhatsappEntrega> reservasAntigas(Instant limite) {
        return em.createQuery("select e from WhatsappEntrega e where e.status = :status and e.processamentoEm < :limite", WhatsappEntrega.class)
                .setParameter("status", StatusEntrega.PROCESSANDO).setParameter("limite", limite).getResultList();
    }
    public List<UUID> expiradasCandidatas(LocalDate hoje, boolean depoisDas18) {
        return em.createQuery("select e.id from WhatsappEntrega e where e.status = :pendente and (e.dataReferencia < :hoje or (e.dataReferencia = :hoje and e.lote.origem = :auto and :depois = true))", UUID.class)
                .setParameter("pendente", StatusEntrega.PENDENTE).setParameter("hoje", hoje)
                .setParameter("auto", com.example.integrador.Whatsapp.WhatsappTipos.Origem.AUTOMATICA)
                .setParameter("depois", depoisDas18).getResultList();
    }
    public WhatsappEntrega porMensagem(String id) {
        return em.createQuery("select e from WhatsappEntrega e where e.mensagemMetaId = :id", WhatsappEntrega.class)
                .setParameter("id", id).getResultStream().findFirst().orElse(null);
    }
    public <T> T buscar(Class<T> classe, Object id) { return em.find(classe, id); }
    public void persistir(Object objeto) { em.persist(objeto); }
}
