package com.example.integrador.Whatsapp;

import com.example.integrador.Whatsapp.WhatsappDto.*;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/integracoes/whatsapp")
public class WhatsappAdminController {
    private final WhatsappAdministracaoService administracao;
    private final WhatsappLoteService lotes;
    private final ProgramacaoPdfService pdf;
    public WhatsappAdminController(WhatsappAdministracaoService administracao, WhatsappLoteService lotes, ProgramacaoPdfService pdf) {
        this.administracao = administracao; this.lotes = lotes; this.pdf = pdf;
    }
    @GetMapping("/configuracao") public ConfiguracaoResponse configuracao() { return administracao.consultar(); }
    @PutMapping("/configuracao") public ConfiguracaoResponse configurar(@Valid @RequestBody ConfiguracaoRequest request) { return administracao.atualizar(request); }
    @GetMapping("/previa") public PreviaResponse previa(@RequestParam Long usuarioId) { return lotes.previa(usuarioId); }
    @GetMapping("/previa/pdf")
    public ResponseEntity<byte[]> previaPdf(@RequestParam Long usuarioId) {
        var programacao = lotes.previa(usuarioId).programacao();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("programacao-" + programacao.dataReferencia() + ".pdf").build().toString())
                .body(pdf.gerar(programacao));
    }
    @PostMapping("/envios")
    public ResponseEntity<LoteResponse> enviar(@Valid @RequestBody EnvioRequest request) {
        return ResponseEntity.accepted().body(lotes.solicitarManual(request));
    }
    @GetMapping("/envios") public HistoricoResponse historico(@RequestParam(defaultValue = "0") int pagina, @RequestParam(defaultValue = "20") int tamanho) {
        return lotes.historico(pagina, tamanho);
    }
    @GetMapping("/envios/{id}") public LoteResponse lote(@PathVariable UUID id) { return lotes.detalhar(id); }
}
