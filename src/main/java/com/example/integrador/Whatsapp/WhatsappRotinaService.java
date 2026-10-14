package com.example.integrador.Whatsapp;

import com.example.integrador.Config.DashboardClockConfig;
import com.example.integrador.Exception.IntegracaoIndisponivelException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.time.*;

@Service
public class WhatsappRotinaService {
    private static final Logger LOG = LoggerFactory.getLogger(WhatsappRotinaService.class);
    private final WhatsappProperties properties;
    private final WhatsappDisponibilidadeService disponibilidade;
    private final WhatsappLoteService lotes;
    private final WhatsappFilaService fila;
    private final Clock clock;
    public WhatsappRotinaService(WhatsappProperties properties, WhatsappDisponibilidadeService disponibilidade,
                                WhatsappLoteService lotes, WhatsappFilaService fila, Clock clock) {
        this.properties = properties; this.disponibilidade = disponibilidade; this.lotes = lotes; this.fila = fila; this.clock = clock;
    }
    @Scheduled(cron = "0 0 15 * * *", zone = "America/Sao_Paulo")
    public void agendado() { if (properties.isSchedulersEnabled()) recuperarHoje(); }

    @Scheduled(cron = "0 * 15-17 * * *", zone = "America/Sao_Paulo")
    public void recuperacao() { if (properties.isSchedulersEnabled()) recuperarHoje(); }

    public void recuperarHoje() {
        LocalTime hora = LocalTime.ofInstant(clock.instant(), DashboardClockConfig.ZONA);
        if (hora.isBefore(LocalTime.of(15, 0)) || !hora.isBefore(LocalTime.of(18, 0))) return;
        var config = disponibilidade.consultar();
        if (!config.automatico() || !config.integracaoHabilitada()) return;
        try { lotes.solicitarAutomatico(); }
        catch (IntegracaoIndisponivelException exception) { LOG.warn("Envio automático aguardando configuração válida do WhatsApp"); }
    }
    @Scheduled(fixedDelay = 1000)
    public void processarFila() {
        if (!properties.isSchedulersEnabled()) return;
        try { fila.processarProxima(); }
        catch (RuntimeException exception) { LOG.error("Não foi possível verificar a fila de WhatsApp; nenhum reenvio indiscriminado foi solicitado"); }
    }
}
