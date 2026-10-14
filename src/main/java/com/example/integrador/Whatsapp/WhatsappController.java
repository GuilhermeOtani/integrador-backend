package com.example.integrador.Whatsapp;

import com.example.integrador.Whatsapp.WhatsappDto.*;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/whatsapp")
public class WhatsappController {
    private final WhatsappAssinaturaService assinaturas;
    private final WhatsappConsultaService consulta;
    private final ProgramacaoPdfService pdf;
    public WhatsappController(WhatsappAssinaturaService assinaturas, WhatsappConsultaService consulta, ProgramacaoPdfService pdf) {
        this.assinaturas = assinaturas; this.consulta = consulta; this.pdf = pdf;
    }
    @GetMapping("/minha-assinatura")
    public AssinaturaResponse assinatura() { return assinaturas.consultarMinha(); }
    @PostMapping("/minha-assinatura/ativacao")
    public AtivacaoResponse ativar(@Valid @RequestBody AtivacaoRequest request) { return assinaturas.solicitar(request); }
    @DeleteMapping("/minha-assinatura")
    public ResponseEntity<Void> cancelar() { assinaturas.cancelarMinha(); return ResponseEntity.noContent().build(); }
    @GetMapping("/minha-programacao/pdf")
    public ResponseEntity<byte[]> programacao() {
        var programacao = consulta.minhaProgramacao();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("programacao-" + programacao.dataReferencia() + ".pdf").build().toString())
                .body(pdf.gerar(programacao));
    }
}
