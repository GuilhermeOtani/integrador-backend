package com.example.integrador.Email;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/integracoes/email")
public class EmailAdminController {
    private final EmailAdministracaoService administracao;
    private final EmailLoteService lotes;
    public EmailAdminController(EmailAdministracaoService administracao, EmailLoteService lotes) {
        this.administracao = administracao;
        this.lotes = lotes;
    }
    @GetMapping("/configuracao")
    public EmailDto.ConfiguracaoResponse configuracao() { return administracao.consultar(); }

    @PutMapping("/configuracao")
    public EmailDto.ConfiguracaoResponse configurar(@Valid @RequestBody EmailDto.ConfiguracaoRequest request) {
        return administracao.atualizar(request.automatico());
    }
    @GetMapping("/previa")
    public EmailDto.Previa previa() { return lotes.previa(); }

    @PostMapping("/envios")
    public ResponseEntity<EmailDto.LoteResponse> enviar(@Valid @RequestBody EmailDto.EnvioRequest request) {
        return ResponseEntity.accepted().body(lotes.solicitar(request));
    }
    @GetMapping("/envios")
    public EmailDto.Historico historico(@RequestParam(defaultValue = "0") int pagina,
                                      @RequestParam(defaultValue = "20") int tamanho) {
        return lotes.historico(pagina, tamanho);
    }
    @GetMapping("/envios/{id}")
    public EmailDto.LoteResponse detalhe(@PathVariable UUID id) { return lotes.detalhe(id); }
}
