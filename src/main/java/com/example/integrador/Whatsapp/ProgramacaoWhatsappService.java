package com.example.integrador.Whatsapp;

import com.example.integrador.Config.DashboardClockConfig;
import com.example.integrador.Enum.TipoPessoa;
import com.example.integrador.Exception.RegraNegocioException;
import com.example.integrador.Model.*;
import com.example.integrador.Service.AgendaTransporteService;
import com.example.integrador.Whatsapp.WhatsappDto.Programacao;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.time.format.TextStyle;
import java.util.Locale;

@Service
@Transactional(readOnly = true)
public class ProgramacaoWhatsappService {
    private final AgendaTransporteService agenda;
    private final Clock clock;
    public ProgramacaoWhatsappService(AgendaTransporteService agenda, Clock clock) { this.agenda = agenda; this.clock = clock; }
    public LocalDate hoje() { return LocalDate.ofInstant(clock.instant(), DashboardClockConfig.ZONA); }
    public Programacao consultar(Usuario usuario) {
        LocalDate data = hoje();
        String dia = data.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.forLanguageTag("pt-BR"));
        if (usuario.getPessoa() instanceof Aluno aluno) {
            String referencia = aluno.getFaculdade() == null ? "Faculdade não informada"
                    : valor(aluno.getFaculdade().getNome(), "Faculdade " + aluno.getFaculdade().getId());
            return new Programacao(data, dia, TipoPessoa.ALUNO, referencia, agenda.aluno(aluno, data).viagensHoje());
        }
        if (usuario.getPessoa() instanceof Motorista motorista) {
            return new Programacao(data, dia, TipoPessoa.MOTORISTA, "Suas viagens atribuídas", agenda.motorista(motorista, data).viagensHoje());
        }
        throw new RegraNegocioException("Somente alunos e motoristas possuem programação individual");
    }
    static String valor(String valor, String padrao) { return valor == null || valor.isBlank() ? padrao : valor; }
}
