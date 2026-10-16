package com.example.integrador.Controller;

import com.example.integrador.DTO.RelatoriosDTO.*;
import com.example.integrador.Service.RelatoriosService;
import com.example.integrador.Service.RelatorioPdfService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;

@RestController
@RequestMapping("/relatorios")
public class RelatoriosController {
    private final RelatoriosService consultas;
    private final RelatorioPdfService pdf;
    public RelatoriosController(RelatoriosService consultas, RelatorioPdfService pdf) { this.consultas = consultas; this.pdf = pdf; }
    @GetMapping("/inadimplencia")
    public Inadimplencia inadimplencia(@RequestParam(required = false) Long alunoId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate vencimentoInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate vencimentoFim) {
        return consultas.inadimplencia(alunoId, vencimentoInicio, vencimentoFim);
    }
    @GetMapping("/inadimplencia/pdf")
    public ResponseEntity<byte[]> inadimplenciaPdf(@RequestParam(required = false) Long alunoId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate vencimentoInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate vencimentoFim) {
        return arquivo("inadimplencia.pdf", pdf.inadimplencia(alunoId, vencimentoInicio, vencimentoFim));
    }
    @GetMapping("/fluxo-caixa")
    public FluxoCaixa fluxoCaixa(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim) { return consultas.fluxoCaixa(dataInicio, dataFim); }
    @GetMapping("/fluxo-caixa/pdf")
    public ResponseEntity<byte[]> fluxoCaixaPdf(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim) {
        return arquivo("fluxo-caixa.pdf", pdf.fluxoCaixa(dataInicio, dataFim));
    }
    @GetMapping("/acerto-motoristas")
    public Acerto acerto(@RequestParam String mes, @RequestParam(required = false) Long motoristaId) { return consultas.acerto(mes, motoristaId); }
    @GetMapping("/acerto-motoristas/pdf")
    public ResponseEntity<byte[]> acertoPdf(@RequestParam String mes, @RequestParam(required = false) Long motoristaId) {
        return arquivo("acerto-motoristas.pdf", pdf.acerto(mes, motoristaId));
    }
    private ResponseEntity<byte[]> arquivo(String nome, byte[] conteudo) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(nome).build().toString())
                .cacheControl(CacheControl.noStore()).body(conteudo);
    }
}
