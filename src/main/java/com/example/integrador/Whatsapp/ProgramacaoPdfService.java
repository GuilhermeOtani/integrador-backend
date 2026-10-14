package com.example.integrador.Whatsapp;

import com.example.integrador.DTO.DashboardResponse.*;
import com.example.integrador.Exception.RecursoNaoEncontradoException;
import com.example.integrador.Whatsapp.WhatsappDto.Programacao;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.*;
import org.springframework.stereotype.Service;
import java.awt.Color;
import java.io.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Documentos gerados em memória, com fontes embarcadas e sem acesso a URLs do cliente. */
@Service
public class ProgramacaoPdfService {
    public byte[] gerar(Programacao programacao) {
        if (programacao.viagens().isEmpty()) throw new RecursoNaoEncontradoException("Não há programação para este dia");
        try (var documento = new PDDocument();
             var regular = fonte("Vera.ttf"); var negrito = fonte("VeraBd.ttf");
             var saida = new ByteArrayOutputStream()) {
            PDType0Font normal = PDType0Font.load(documento, regular);
            PDType0Font bold = PDType0Font.load(documento, negrito);
            var layout = new Layout(documento, normal, bold);
            layout.titulo("Programação de transporte");
            layout.texto(programacao.dataReferencia().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) + " - " + programacao.diaSemana(), true);
            layout.texto(programacao.referencia(), false);
            layout.texto("Programação pela grade semanal. Sem horários de saída cadastrados.", false);
            for (var viagem : programacao.viagens()) {
                layout.espaco();
                layout.titulo("Viagem " + viagem.id() + " - " + (viagem.rota() == null ? "Rota não definida" : viagem.rota().nome()));
                if (viagem.grade() != null) layout.texto("Grade: " + valor(viagem.grade().descricao()), false);
                var onibus = viagem.onibus();
                layout.texto(onibus == null ? "Ônibus não definido" : "Ônibus: " + valor(onibus.numeroIdentificacao())
                        + " | Placa: " + valor(onibus.placa()) + " | Estado: " + valor(onibus.statusOnibus()), true);
                if (onibus != null) layout.texto("Modelo: " + valor(onibus.modelo()) + " | Capacidade: " + valor(onibus.capacidade()), false);
                layout.texto("Motorista: " + (viagem.motorista() == null ? "Não definido" : valor(viagem.motorista().nome())), false);
                if (viagem.rota() != null) {
                    layout.texto("Itinerário: " + valor(viagem.rota().descricao()), false);
                    layout.texto("Pontos de embarque:", true);
                    if (viagem.rota().pontosEmbarque().isEmpty()) layout.texto("Nenhum ponto cadastrado.", false);
                    int ordem = 1;
                    for (var ponto : viagem.rota().pontosEmbarque()) layout.texto(ordem++ + ". " + valor(ponto.descricao()), false);
                }
                layout.texto("Faculdades atendidas:", true);
                for (var faculdade : viagem.faculdades()) layout.texto("- " + valor(faculdade.nome()) + " | " + valor(faculdade.endereco()), false);
                for (String aviso : viagem.avisos()) layout.aviso("ATENÇÃO: " + aviso);
            }
            layout.finalizar();
            documento.save(saida);
            return saida.toByteArray();
        } catch (IOException exception) { throw new IllegalStateException("Não foi possível gerar o PDF da programação", exception); }
    }
    private InputStream fonte(String nome) throws IOException {
        InputStream arquivo = getClass().getResourceAsStream("/fonts/" + nome);
        if (arquivo == null) throw new IOException("Fonte embarcada não encontrada");
        return arquivo;
    }
    private static String valor(Object valor) { return valor == null ? "Não informado" : valor.toString(); }

    private static final class Layout {
        private static final float MARGEM = 48, LIMITE_INFERIOR = 58;
        private final PDDocument documento;
        private final PDType0Font normal, bold;
        private PDPageContentStream conteudo;
        private float y;
        private Color cor = new Color(30, 41, 59);
        Layout(PDDocument documento, PDType0Font normal, PDType0Font bold) throws IOException {
            this.documento = documento; this.normal = normal; this.bold = bold; novaPagina();
        }
        void novaPagina() throws IOException {
            if (conteudo != null) conteudo.close();
            var pagina = new PDPage(PDRectangle.A4); documento.addPage(pagina);
            conteudo = new PDPageContentStream(documento, pagina);
            y = PDRectangle.A4.getHeight() - MARGEM;
        }
        void titulo(String valor) throws IOException {
            if (y < LIMITE_INFERIOR + 80) novaPagina();
            bloco(valor, bold, 14);
        }
        void texto(String valor, boolean destaque) throws IOException {
            if (destaque && y < LIMITE_INFERIOR + 30) novaPagina();
            bloco(valor, destaque ? bold : normal, 10);
        }
        void aviso(String valor) throws IOException {
            cor = new Color(153, 27, 27); texto(valor, true); cor = new Color(30, 41, 59);
        }
        void espaco() { y -= 12; }
        void bloco(String valor, PDType0Font fonte, float tamanho) throws IOException {
            String limpo = limpar(valor == null ? "Não informado" : valor, fonte);
            StringBuilder linha = new StringBuilder();
            float largura = PDRectangle.A4.getWidth() - MARGEM * 2;
            for (int codigo : limpo.codePoints().toArray()) {
                String letra = new String(Character.toChars(codigo));
                if (codigo == '\n') { linha(linha.toString(), fonte, tamanho); linha.setLength(0); continue; }
                if (!linha.isEmpty() && fonte.getStringWidth(linha + letra) / 1000 * tamanho > largura) {
                    int separador = linha.lastIndexOf(" ");
                    String resto = separador > 0 ? linha.substring(separador + 1) : "";
                    linha(separador > 0 ? linha.substring(0, separador) : linha.toString(), fonte, tamanho);
                    linha.setLength(0); linha.append(resto);
                }
                linha.append(letra);
            }
            if (!linha.isEmpty()) linha(linha.toString().stripTrailing(), fonte, tamanho);
            y -= 5;
        }
        String limpar(String valor, PDType0Font fonte) throws IOException {
            StringBuilder texto = new StringBuilder();
            for (int codigo : valor.codePoints().toArray()) {
                if (codigo == '\n') { texto.append('\n'); continue; }
                if (Character.isISOControl(codigo)) { texto.append(' '); continue; }
                String letra = new String(Character.toChars(codigo));
                try { fonte.encode(letra); texto.append(letra); }
                catch (IllegalArgumentException exception) { texto.append('?'); }
            }
            return texto.toString();
        }
        void linha(String texto, PDFont fonte, float tamanho) throws IOException {
            if (y < LIMITE_INFERIOR + tamanho) novaPagina();
            conteudo.beginText(); conteudo.setNonStrokingColor(cor);
            conteudo.setFont(fonte, tamanho); conteudo.newLineAtOffset(MARGEM, y); conteudo.showText(texto); conteudo.endText();
            y -= tamanho + 5;
        }
        void finalizar() throws IOException {
            conteudo.close();
            int total = documento.getNumberOfPages();
            for (int i = 0; i < total; i++) {
                try (var rodape = new PDPageContentStream(documento, documento.getPage(i), PDPageContentStream.AppendMode.APPEND, true, true)) {
                    rodape.beginText(); rodape.setNonStrokingColor(new Color(100, 116, 139)); rodape.setFont(normal, 8);
                    rodape.newLineAtOffset(MARGEM, 30);
                    rodape.showText("Transporte - Página " + (i + 1) + " de " + total); rodape.endText();
                }
            }
        }
    }
}
