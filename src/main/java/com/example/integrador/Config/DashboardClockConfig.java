package com.example.integrador.Config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class DashboardClockConfig {
    public static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");

    @Bean
    public Clock dashboardClock() { return Clock.system(ZONA); }
}
