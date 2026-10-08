package com.example.integrador.Controller;

import com.example.integrador.DTO.RecebimentoResponse;
import com.example.integrador.Enum.FormaPagamento;
import com.example.integrador.Service.RecebimentoService;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/recebimentos")
public class RecebimentoController {
    private final RecebimentoService recebimentoService;

    public RecebimentoController(RecebimentoService recebimentoService) {
        this.recebimentoService = recebimentoService;
    }

    @PostMapping("/realizar/{contaId}")
    public RecebimentoResponse realizarPagamento(
            @PathVariable Long contaId,
            @RequestParam FormaPagamento formaPagamento,
            JwtAuthenticationToken authentication) {
        return recebimentoService.realizarPagamento(
                contaId, formaPagamento, pessoaId(authentication));
    }

    @GetMapping("/recibo/conta/{contaId}")
    public RecebimentoResponse buscarRecibo(
            @PathVariable Long contaId,
            JwtAuthenticationToken authentication) {
        return recebimentoService.buscarRecibo(contaId, pessoaId(authentication));
    }

    private Long pessoaId(JwtAuthenticationToken authentication) {
        Number pessoaId = authentication.getToken().getClaim("pessoaId");
        return pessoaId.longValue();
    }
}
