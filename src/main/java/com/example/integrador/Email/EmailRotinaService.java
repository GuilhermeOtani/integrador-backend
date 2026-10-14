package com.example.integrador.Email;

import com.example.integrador.Config.DashboardClockConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.time.Clock;
import java.time.LocalTime;

@Service
public class EmailRotinaService {
    private static final Logger LOG = LoggerFactory.getLogger(EmailRotinaService.class);
    private final EmailProperties properties;
    private final EmailLoteService lotes;
    private final EmailFilaService fila;
    private final Clock clock;

    public EmailRotinaService(EmailProperties properties, EmailLoteService lotes, EmailFilaService fila, Clock clock) {
        this.properties = properties;
        this.lotes = lotes;
        this.fila = fila;
        this.clock = clock;
    }
    @Scheduled(cron = "0 0 15 * * *", zone = "America/Sao_Paulo")
    public void enviarAs15() { recuperar(); }

    @Scheduled(cron = "0 * 15-17 * * *", zone = "America/Sao_Paulo")
    public void recuperar() {
        if (!properties.isSchedulersEnabled() || !properties.isEnabled()) return;
        LocalTime hora = LocalTime.ofInstant(clock.instant(), DashboardClockConfig.ZONA);
        if (hora.isBefore(LocalTime.of(15, 0)) || !hora.isBefore(LocalTime.of(18, 0))) return;
        try { lotes.solicitarAutomatico(); }
        catch (RuntimeException exception) { LOG.warn("Rotina de e-mail indisponível; revise a configuração no painel administrativo."); }
    }
    @Scheduled(fixedDelay = 1000)
    public void processarFila() {
        if (!properties.isSchedulersEnabled() || !properties.isEnabled()) return;
        try { fila.processarUma(); }
        catch (RuntimeException exception) { LOG.warn("Fila de e-mail indisponível; consulte o histórico administrativo."); }
    }
}
