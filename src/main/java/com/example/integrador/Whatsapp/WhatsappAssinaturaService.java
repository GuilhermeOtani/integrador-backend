package com.example.integrador.Whatsapp;

import com.example.integrador.Exception.*;
import com.example.integrador.Whatsapp.WhatsappDto.*;
import com.example.integrador.Whatsapp.WhatsappTipos.StatusAssinatura;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.*;

@Service
public class WhatsappAssinaturaService {
    private final WhatsappRepos repos;
    private final WhatsappIdentidade identidade;
    private final WhatsappDisponibilidadeService disponibilidade;
    private final WhatsappProperties properties;
    private final Clock clock;
    public WhatsappAssinaturaService(WhatsappRepos repos, WhatsappIdentidade identidade,
                                    WhatsappDisponibilidadeService disponibilidade, WhatsappProperties properties, Clock clock) {
        this.repos = repos; this.identidade = identidade; this.disponibilidade = disponibilidade; this.properties = properties; this.clock = clock;
    }
    @PreAuthorize("hasAnyRole('ALUNO','MOTORISTA')")
    @Transactional(readOnly = true)
    public AssinaturaResponse consultarMinha() { return resposta(repos.assinatura(identidade.atual().getId())); }

    @PreAuthorize("hasAnyRole('ALUNO','MOTORISTA')")
    @Transactional
    public AtivacaoResponse solicitar(AtivacaoRequest request) {
        if (!Boolean.TRUE.equals(request.aceite())) throw new RegraNegocioException("É necessário autorizar o recebimento");
        disponibilidade.exigirAtivacaoDisponivel();
        String numero = WhatsappSeguranca.numero(request.numero());
        if (!disponibilidade.permitido(numero)) throw new RegraNegocioException("Número não autorizado no ambiente de teste");
        repos.bloquearConfiguracao();
        var usuario = identidade.atual();
        for (var outra : repos.porNumero(numero)) {
            if (outra.getStatus() == StatusAssinatura.ATIVA && !outra.getUsuario().getId().equals(usuario.getId())) {
                throw new ConflitoException("Este número já possui recebimento ativo");
            }
        }
        Instant agora = clock.instant();
        var assinatura = repos.assinatura(usuario.getId());
        boolean nova = assinatura == null;
        if (assinatura == null) {
            assinatura = new WhatsappAssinatura(); assinatura.setUsuario(usuario);
            assinatura.setJanelaAtivacoes(agora); assinatura.setRevisao(0);
        }
        if (!assinatura.getJanelaAtivacoes().isAfter(agora.minus(Duration.ofHours(1)))) {
            assinatura.setJanelaAtivacoes(agora); assinatura.setAtivacoesNaJanela(0);
        }
        if (assinatura.getAtivacoesNaJanela() >= 5) throw new ConflitoException("Limite de cinco ativações por hora atingido");
        String codigo = WhatsappSeguranca.codigo();
        assinatura.setAtivacoesNaJanela(assinatura.getAtivacoesNaJanela() + 1);
        assinatura.setNumero(numero); assinatura.setStatus(StatusAssinatura.PENDENTE);
        assinatura.setConsentimentoEm(agora); assinatura.setConfirmadoEm(null); assinatura.setCanceladoEm(null);
        assinatura.setCodigoHash(WhatsappSeguranca.hash(codigo)); assinatura.setCodigoExpiraEm(agora.plus(Duration.ofMinutes(30)));
        assinatura.setRevisao(assinatura.getRevisao() + 1);
        if (nova) repos.persistir(assinatura);
        String link = "https://wa.me/" + properties.getBusinessNumber().substring(1) + "?text="
                + URLEncoder.encode("ATIVAR " + codigo, StandardCharsets.UTF_8);
        return new AtivacaoResponse(resposta(assinatura), link);
    }
    @PreAuthorize("hasAnyRole('ALUNO','MOTORISTA')")
    @Transactional
    public void cancelarMinha() {
        repos.bloquearConfiguracao();
        var assinatura = repos.assinatura(identidade.atual().getId());
        if (assinatura != null) cancelar(assinatura);
    }
    void cancelar(WhatsappAssinatura assinatura) {
        if (assinatura.getStatus() == StatusAssinatura.CANCELADA) return;
        assinatura.setStatus(StatusAssinatura.CANCELADA); assinatura.setCanceladoEm(clock.instant());
        assinatura.setCodigoHash(null); assinatura.setCodigoExpiraEm(null); assinatura.setRevisao(assinatura.getRevisao() + 1);
    }
    static AssinaturaResponse resposta(WhatsappAssinatura assinatura) {
        return assinatura == null ? new AssinaturaResponse(null, "NAO_CADASTRADA", null, null, null, null)
                : new AssinaturaResponse(assinatura.getNumero(), assinatura.getStatus().name(), assinatura.getConsentimentoEm(),
                    assinatura.getConfirmadoEm(), assinatura.getCanceladoEm(), assinatura.getCodigoExpiraEm());
    }
}
