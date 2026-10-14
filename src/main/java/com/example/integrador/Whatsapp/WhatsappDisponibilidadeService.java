package com.example.integrador.Whatsapp;

import com.example.integrador.Exception.IntegracaoIndisponivelException;
import com.example.integrador.Whatsapp.WhatsappDto.ConfiguracaoResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Clock;

@Service
public class WhatsappDisponibilidadeService {
    private final WhatsappProperties properties;
    private final WhatsappRepos repos;
    private final MetaWhatsappGateway meta;
    private final TransactionTemplate tx;
    private final Clock clock;
    public WhatsappDisponibilidadeService(WhatsappProperties properties, WhatsappRepos repos, MetaWhatsappGateway meta, TransactionTemplate tx, Clock clock) {
        this.properties = properties; this.repos = repos; this.meta = meta; this.tx = tx; this.clock = clock;
    }
    public boolean credenciaisConfiguradas() {
        return properties.getGraphApiVersion().matches("v[0-9]+\\.[0-9]+")
            && properties.getWabaId().matches("[0-9]+") && properties.getPhoneNumberId().matches("[0-9]+")
            && !properties.getAccessToken().isBlank() && !properties.getAppSecret().isBlank()
            && !properties.getVerifyToken().isBlank() && !properties.getTemplateName().isBlank()
            && properties.getBusinessNumber().matches("\\+[1-9][0-9]{7,14}")
            && (properties.isProduction() || !properties.getTestAllowedNumbers().isEmpty());
    }
    public boolean permitido(String numero) { return properties.isProduction() || properties.getTestAllowedNumbers().contains(numero); }
    public String webhookFingerprint() {
        return WhatsappSeguranca.hash(properties.getPhoneNumberId() + "|" + properties.getAppSecret() + "|" + properties.getVerifyToken());
    }
    public String templateFingerprint() {
        return WhatsappSeguranca.hash(properties.getPhoneNumberId() + "|" + properties.getWabaId() + "|" + properties.getGraphApiVersion()
                + "|" + properties.getAccessToken() + "|" + properties.getTemplateName());
    }
    public ConfiguracaoResponse consultar() {
        return tx.execute(status -> {
            var cfg = repos.buscar(WhatsappConfig.class, 1L);
            return new ConfiguracaoResponse(properties.isEnabled(), properties.isProduction(), cfg.isAutomatico(),
                    credenciaisConfiguradas(), webhookVerificado(cfg), templateVerificado(cfg),
                    "15:00", "America/Sao_Paulo", "18:00", properties.getBusinessNumber().isBlank() ? null : properties.getBusinessNumber());
        });
    }
    public boolean webhookVerificado(WhatsappConfig cfg) {
        return cfg.getWebhookVerificadoEm() != null && webhookFingerprint().equals(cfg.getWebhookFingerprint());
    }
    public boolean templateVerificado(WhatsappConfig cfg) {
        return cfg.getTemplateVerificadoEm() != null && templateFingerprint().equals(cfg.getTemplateFingerprint());
    }
    public void exigirAtivacaoDisponivel() {
        if (!properties.isEnabled() || !credenciaisConfiguradas() || !consultar().webhookVerificado()) {
            throw new IntegracaoIndisponivelException("Configure a integração e valide o webhook antes de ativar o recebimento");
        }
    }
    public void verificarEnvio() {
        exigirAtivacaoDisponivel();
        try { meta.verificarTemplateENumero(); }
        catch (MetaWhatsappException exception) { throw new IntegracaoIndisponivelException("Verifique o número e o template aprovado na Meta: " + exception.getMessage()); }
        tx.executeWithoutResult(status -> {
            var cfg = repos.bloquearConfiguracao();
            cfg.setTemplateVerificadoEm(clock.instant()); cfg.setTemplateFingerprint(templateFingerprint());
        });
    }
    public boolean envioConfigurado() {
        var cfg = consultar();
        return cfg.integracaoHabilitada() && cfg.credenciaisConfiguradas() && cfg.webhookVerificado() && cfg.templateVerificado();
    }
}
