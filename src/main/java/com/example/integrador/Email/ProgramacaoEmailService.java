package com.example.integrador.Email;

import com.example.integrador.Config.DashboardClockConfig;
import com.example.integrador.DTO.DashboardResponse.ViagemResumo;
import com.example.integrador.Service.AgendaTransporteService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.LocalDate;
import java.util.*;

@Service
public class ProgramacaoEmailService {
    private final AgendaTransporteService agenda;
    private final EmailRepository repository;
    private final EmailDisponibilidadeService disponibilidade;
    private final EmailMensagemService mensagens;
    private final Clock clock;

    public ProgramacaoEmailService(AgendaTransporteService agenda, EmailRepository repository,
            EmailDisponibilidadeService disponibilidade, EmailMensagemService mensagens, Clock clock) {
        this.agenda = agenda;
        this.repository = repository;
        this.disponibilidade = disponibilidade;
        this.mensagens = mensagens;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Preparacao preparar() {
        LocalDate hoje = LocalDate.ofInstant(clock.instant(), DashboardClockConfig.ZONA);
        List<ViagemResumo> viagens = agenda.geralDoDia(hoje);
        var associados = repository.associados();
        Map<String, EmailDto.Destinatario> porEmail = new LinkedHashMap<>();
        for (var associado : associados) {
            String email = EmailDisponibilidadeService.normalizar(associado.email());
            if (disponibilidade.permitido(email))
                porEmail.putIfAbsent(email, new EmailDto.Destinatario(associado.usuarioId(), email));
        }
        List<String> avisos = new ArrayList<>();
        if (viagens.isEmpty()) avisos.add("Não há viagens com grade para o dia de hoje. Nenhum e-mail será enviado.");
        if (porEmail.isEmpty()) avisos.add("Nenhum aluno ou motorista ativo com e-mail permitido.");
        return new Preparacao(new EmailDto.Previa(hoje, clock.instant(), porEmail.size(),
                associados.size() - porEmail.size(), viagens, List.copyOf(avisos), mensagens.gerar(hoje, viagens)),
                List.copyOf(porEmail.values()));
    }
    public record Preparacao(EmailDto.Previa previa, List<EmailDto.Destinatario> destinatarios) {}
}
