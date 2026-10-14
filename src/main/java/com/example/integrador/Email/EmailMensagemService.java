package com.example.integrador.Email;

import com.example.integrador.DTO.DashboardResponse.ViagemResumo;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Corpo comum a todos, agrupado por destino. Só inclui informações operacionais. */
@Service
public class EmailMensagemService {
    private static final Locale BR = Locale.forLanguageTag("pt-BR");
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy", BR);
    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("EEEE", BR);

    public EmailDto.Conteudo gerar(LocalDate data, List<ViagemResumo> viagens) {
        String assunto = "Programação de transporte — " + DATA.format(data) + " (" + DIA.format(data) + ")";
        StringBuilder texto = new StringBuilder(assunto).append("\n\n")
                .append("Programação geral: procure sua faculdade e confira o número do ônibus e o motorista.\n")
                .append("Não há horários cadastrados. Confirme dúvidas com a administração.\n");
        StringBuilder html = new StringBuilder("<!doctype html><html lang=\"pt-BR\"><head><meta charset=\"UTF-8\"></head>")
                .append("<body style=\"font-family:Arial,sans-serif;color:#172554;max-width:800px;margin:auto;padding:20px\">")
                .append("<h1 style=\"font-size:24px\">").append(escapar(assunto)).append("</h1>")
                .append("<p>Procure sua faculdade e confira o <strong>número do ônibus</strong> e o <strong>motorista</strong>.</p>")
                .append("<p>Programação geral do dia. Não há horários cadastrados; confirme dúvidas com a administração.</p>");
        Map<String, List<ViagemResumo>> grupos = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (var viagem : viagens) {
            if (viagem.faculdades().isEmpty()) {
                grupos.computeIfAbsent("Destino não informado", chave -> new ArrayList<>()).add(viagem);
            } else {
                // Uma viagem com múltiplos destinos aparece sob cada faculdade, sem duplicar o envio.
                Set<String> nomes = new LinkedHashSet<>();
                viagem.faculdades().forEach(f -> nomes.add(valor(f.nome(), "Faculdade sem nome")));
                nomes.forEach(nome -> grupos.computeIfAbsent(nome, chave -> new ArrayList<>()).add(viagem));
            }
        }
        if (grupos.isEmpty()) {
            texto.append("\nNão há programação para este dia.\n");
            html.append("<p>Não há programação para este dia.</p>");
        }
        grupos.forEach((faculdade, itens) -> {
            texto.append("\nFACULDADE: ").append(faculdade).append("\n");
            html.append("<h2 style=\"background:#eff6ff;padding:12px;font-size:20px\">")
                    .append(escapar(faculdade)).append("</h2>");
            for (var viagem : itens) {
                String onibus = viagem.onibus() == null ? "Não definido"
                        : valor(viagem.onibus().numeroIdentificacao(), "Sem número de identificação");
                String motorista = viagem.motorista() == null ? "Não definido" : valor(viagem.motorista().nome(), "Nome não informado");
                String rota = viagem.rota() == null ? "Não definida" : valor(viagem.rota().nome(), "Rota sem nome");
                texto.append("Viagem #").append(viagem.id()).append(" | Ônibus: ").append(onibus)
                        .append(" | Motorista: ").append(motorista).append(" | Rota: ").append(rota).append("\n");
                html.append("<div style=\"border:1px solid #cbd5e1;padding:14px;margin-bottom:12px\">")
                        .append("<p><strong>Ônibus: ").append(escapar(onibus)).append("</strong><br>")
                        .append("<strong>Motorista: ").append(escapar(motorista)).append("</strong></p>")
                        .append("<p>Viagem #").append(viagem.id()).append(" — Rota: ").append(escapar(rota)).append("</p>");
                if (viagem.grade() != null && viagem.grade().descricao() != null) {
                    linha(texto, html, "Grade", viagem.grade().descricao());
                }
                if (viagem.rota() != null) {
                    linha(texto, html, "Itinerário", viagem.rota().descricao());
                    if (!viagem.rota().pontosEmbarque().isEmpty()) {
                        String pontos = String.join(" → ", viagem.rota().pontosEmbarque().stream()
                                .map(p -> valor(p.descricao(), "Ponto não informado")).toList());
                        linha(texto, html, "Pontos de embarque", pontos);
                    }
                }
                if (viagem.onibus() != null) {
                    linha(texto, html, "Estado do ônibus", viagem.onibus().statusOnibus() == null
                            ? "Não informado" : viagem.onibus().statusOnibus().name());
                }
                for (String aviso : viagem.avisos()) linha(texto, html, "ATENÇÃO", aviso);
                html.append("</div>");
            }
        });
        texto.append("\nMensagem operacional do sistema de transporte. Esta escala não contém atribuições individuais de alunos.\n");
        html.append("<hr><p style=\"font-size:12px\">Mensagem operacional do sistema de transporte. ")
                .append("Esta escala não contém atribuições individuais de alunos.</p></body></html>");
        return new EmailDto.Conteudo(assunto, texto.toString(), html.toString());
    }

    private void linha(StringBuilder texto, StringBuilder html, String titulo, String conteudo) {
        if (conteudo == null || conteudo.isBlank()) return;
        texto.append(titulo).append(": ").append(conteudo).append("\n");
        html.append("<p>").append(escapar(titulo)).append(": ").append(escapar(conteudo)).append("</p>");
    }
    private String valor(String texto, String substituto) { return texto == null || texto.isBlank() ? substituto : texto; }
    private String escapar(String texto) { return HtmlUtils.htmlEscape(texto, "UTF-8"); }
}
