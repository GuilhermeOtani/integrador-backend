package com.example.integrador.Service;

import com.example.integrador.DTO.RelatoriosDTO.*;
import com.example.integrador.Exception.GeracaoRelatorioException;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@PreAuthorize("hasRole('ADMIN')")
public class RelatorioPdfService {
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter EMISSAO = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm XXX");
    private final Map<String, JasperReport> templates;
    private final RelatoriosService relatorios;
    public RelatorioPdfService(RelatoriosService relatorios) throws JRException, IOException {
        this.relatorios = relatorios;
        Map<String, JasperReport> compilados = new HashMap<>();
        for (String nome : List.of("inadimplencia", "fluxo-caixa", "acerto-motoristas")) {
            try (var arquivo = new ClassPathResource("relatorios/" + nome + ".jrxml").getInputStream()) {
                compilados.put(nome, JasperCompileManager.compileReport(arquivo));
            }
        }
        templates = Map.copyOf(compilados);
    }
    public byte[] inadimplencia(Long alunoId, LocalDate inicio, LocalDate fim) {
        var r = relatorios.inadimplencia(alunoId, inicio, fim);
        List<Map<String, ?>> linhas = new ArrayList<>();
        for (var a : r.alunos()) for (var c : a.cobrancas()) linhas.add(linha(a.nome() + " | #" + a.alunoId(),
                "Matrícula: " + texto(a.matricula()) + " | Telefone: " + texto(a.telefone()) + (a.arquivado() ? " | Arquivado" : ""),
                "Total do aluno: " + moeda(a.total()), "#" + c.contaId() + " | " + texto(c.descricao()) + " | Original: " + moeda(c.valor()) + " | Recebido: " + moeda(c.valorRecebido()) + " | Competência: " + texto(c.competencia()), data(c.vencimento()), c.diasAtraso() + " dias", moeda(c.saldoRestante())));
        return gerar("inadimplencia", "Inadimplência de alunos",
                "Posição em " + data(r.dataReferencia()) + " | Vencimentos: " + data(inicio) + " a " + data(fim) + (alunoId == null ? " | Todos os alunos" : " | Aluno #" + alunoId),
                "  Alunos: " + r.alunos().size() + "    Cobranças: " + r.quantidadeCobrancas() + "    Em aberto: " + moeda(r.totalGeral()),
                r.geradoEm().format(EMISSAO), r.avisos(), linhas, 4);
    }
    public byte[] fluxoCaixa(LocalDate inicio, LocalDate fim) {
        var r = relatorios.fluxoCaixa(inicio, fim);
        List<Map<String, ?>> linhas = new ArrayList<>();
        for (var m : r.movimentos()) linhas.add(linha("Movimentações financeiras", "Saldo base: " + moeda(r.saldoBase().saldo()) + " em " + data(r.saldoBase().dataReferencia()),
                "Saldo final: " + moeda(r.saldoFinal()), data(m.data()), m.tipo() == TipoMovimento.ENTRADA ? "Entrada" : "Saída",
                texto(m.descricao()) + " | " + texto(m.pessoa()) + " | #" + m.id(),
                m.tipo() == TipoMovimento.ENTRADA ? moeda(m.valor()) : "", m.tipo() == TipoMovimento.SAIDA ? moeda(m.valor()) : "", moeda(m.saldoAcumulado())));
        return gerar("fluxo-caixa", "Fluxo de caixa", "Período: " + data(inicio) + " a " + data(fim) + " | Datas efetivas de movimentação",
                "  Inicial: " + moeda(r.saldoInicial()) + "    Entradas: " + moeda(r.totalEntradas()) + "    Saídas: " + moeda(r.totalSaidas()) + "    Final: " + moeda(r.saldoFinal()),
                r.geradoEm().format(EMISSAO), r.avisos(), linhas, 6);
    }
    public byte[] acerto(String mes, Long motoristaId) {
        var r = relatorios.acerto(mes, motoristaId);
        List<Map<String, ?>> linhas = new ArrayList<>();
        List<String> avisos = new ArrayList<>(r.avisos());
        for (var m : r.motoristas()) for (var c : m.lancamentos()) {
            linhas.add(linha(m.nome() + " | #" + m.motoristaId(), "Salários: " + moeda(m.totalSalarios()) + " | Despesas: " + moeda(m.totalDespesas()),
                    "Previsto: " + moeda(m.totalPrevisto()) + " | Pago: " + moeda(m.totalPago()) + " | A pagar: " + moeda(m.saldoAPagar()),
                    "#" + c.contaId() + " | " + c.classificacao() + " | " + texto(c.descricao()) + (c.pagamentoParcial() ? " | Parcial" : ""),
                    data(c.vencimento()), moeda(c.valor()), moeda(c.valorPago()), moeda(c.saldoRestante())));
            for (String aviso : c.avisos()) avisos.add("Conta " + c.contaId() + ": " + aviso);
        }
        return gerar("acerto-motoristas", "Acerto de motoristas", "Competência: " + mes + " | Pagamentos na posição de " + data(r.dataReferencia()) + (motoristaId == null ? " | Todos os motoristas" : " | Motorista #" + motoristaId),
                "  Previsto: " + moeda(r.totalPrevisto()) + "    Pago: " + moeda(r.totalPago()) + "    A pagar: " + moeda(r.saldoAPagar()),
                r.geradoEm().format(EMISSAO), avisos, linhas, 5);
    }
    private byte[] gerar(String template, String titulo, String filtros, String resumo, String emissao,
                         List<String> avisos, List<Map<String, ?>> linhas, int colunas) {
        Map<String, Object> parametros = new HashMap<>();
        parametros.put("TITULO", titulo); parametros.put("FILTROS", filtros); parametros.put("RESUMO", resumo);
        parametros.put("EMISSAO", "Emitido em " + emissao);
        parametros.put("AVISOS", avisos.isEmpty() ? "" : "Pendências para revisão:\n" + String.join("\n", avisos));
        parametros.put(JRParameter.REPORT_LOCALE, Locale.forLanguageTag("pt-BR"));
        if (linhas.isEmpty()) {
            String[] valores = new String[colunas]; Arrays.fill(valores, ""); valores[0] = "Nenhum registro para os filtros selecionados.";
            linhas.add(linha("Sem lançamentos", "", "", valores));
        }
        try {
            JasperPrint print = JasperFillManager.fillReport(templates.get(template), parametros, new JRMapCollectionDataSource(linhas));
            return JasperExportManager.exportReportToPdf(print);
        } catch (JRException exception) { throw new GeracaoRelatorioException(exception); }
    }
    private Map<String, ?> linha(String grupo, String detalhe, String subtotal, String... colunas) {
        Map<String, Object> linha = new HashMap<>();
        linha.put("grupo", grupo); linha.put("grupoDetalhe", detalhe); linha.put("subtotal", subtotal);
        for (int i = 0; i < colunas.length; i++) linha.put("coluna" + (i + 1), colunas[i]);
        return linha;
    }
    private String moeda(BigDecimal valor) { return valor == null ? "Não informado" : NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).format(valor); }
    private String data(LocalDate valor) { return valor == null ? "Sem limite" : DATA.format(valor); }
    private String texto(String valor) { return valor == null || valor.isBlank() ? "Não informado" : valor; }
}
