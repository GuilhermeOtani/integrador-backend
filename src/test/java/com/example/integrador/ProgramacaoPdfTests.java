package com.example.integrador;

import com.example.integrador.DTO.DashboardResponse.*;
import com.example.integrador.Enum.*;
import com.example.integrador.Whatsapp.*;
import com.example.integrador.Whatsapp.WhatsappDto.Programacao;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class ProgramacaoPdfTests {
    @Test void documentoCurtoComAcentosEAlertas() throws Exception {
        var dados = fixture(1, false);
        byte[] bytes = new ProgramacaoPdfService().gerar(dados);
        Files.createDirectories(Path.of("target/whatsapp-pdf"));
        Files.write(Path.of("target/whatsapp-pdf/curto.pdf"), bytes);
        try (var documento = Loader.loadPDF(bytes)) {
            assertThat(documento.getNumberOfPages()).isEqualTo(1);
            assertThat(new PDFTextStripper().getText(documento)).contains("João", "Praça", "Faculdade São Luís", "Página 1 de 1", "ATENÇÃO");
            assertThat(documento.getPage(0).getResources().getFontNames()).allSatisfy(nome -> {
                try { assertThat(documento.getPage(0).getResources().getFont(nome).isEmbedded()).isTrue(); }
                catch (Exception exception) { throw new IllegalStateException(exception); }
            });
        }
    }
    @Test void documentoLongoQuebraTextoPalavrasExtensasEPaginasSemPerderConteudo() throws Exception {
        byte[] bytes = new ProgramacaoPdfService().gerar(fixture(8, true));
        Files.createDirectories(Path.of("target/whatsapp-pdf"));
        Files.write(Path.of("target/whatsapp-pdf/longo.pdf"), bytes);
        try (var documento = Loader.loadPDF(bytes)) {
            assertThat(documento.getNumberOfPages()).isGreaterThan(2);
            String texto = new PDFTextStripper().getText(documento);
            assertThat(texto).contains("Viagem 8", "Último ponto", "Faculdade São Luís");
            for (int i = 1; i <= documento.getNumberOfPages(); i++) assertThat(texto).contains("Página " + i + " de " + documento.getNumberOfPages());
        }
    }
    @Test void semViagemNaoGeraPdfVazio() {
        assertThatThrownBy(() -> new ProgramacaoPdfService().gerar(new Programacao(LocalDate.of(2026, 10, 14), "quarta-feira", TipoPessoa.ALUNO, "Faculdade", List.of())))
                .hasMessageContaining("Não há programação");
    }
    private Programacao fixture(int quantidade, boolean longo) {
        var faculdades = List.of(new FaculdadeResumo(1L, "Faculdade São Luís", "Rua das Acácias, número 123"));
        var pontos = new ArrayList<PontoResumo>();
        for (int i = 1; i <= (longo ? 8 : 2); i++) pontos.add(new PontoResumo((long) i, "Praça São João - ponto " + i, i));
        pontos.add(new PontoResumo(99L, "Último ponto", null));
        var viagens = new ArrayList<ViagemResumo>();
        for (int i = 1; i <= quantidade; i++) {
            String descricao = longo ? "Itinerário com descrição longa, acentuação e explicação dos pontos de embarque. ".repeat(14) + "X".repeat(200) : "Saída do Centro para as faculdades.";
            viagens.add(new ViagemResumo((long) i, new GradeResumo(1L, DiaSemana.QUARTA, "Grade de quarta-feira"), LocalDate.of(2026, 10, 14),
                    new RotaResumo(1L, "Centro e faculdades", descricao, pontos),
                    new OnibusResumo(1L, "01", "ABC1234", "Modelo de ônibus", 40, StatusOnibus.MANUTENCAO),
                    new PessoaResumo(1L, "João"), faculdades, false,
                    List.of("Ônibus indisponível. Confirme a escala com a administração.")));
        }
        return new Programacao(LocalDate.of(2026, 10, 14), "quarta-feira", TipoPessoa.ALUNO, "Faculdade São Luís", viagens);
    }
}
