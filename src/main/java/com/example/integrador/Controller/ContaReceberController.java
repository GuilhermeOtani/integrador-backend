package com.example.integrador.Controller;

import com.example.integrador.DTO.ContaReceberResponse;
import com.example.integrador.Service.ContaReceberService;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/contareceber")
public class ContaReceberController {
    private final ContaReceberService contaReceberService;

    public ContaReceberController(ContaReceberService contaReceberService) {
        this.contaReceberService = contaReceberService;
    }

    @GetMapping("/listar")
    public List<ContaReceberResponse> listarTodas() {
        return contaReceberService.listarTodasContaReceber();
    }

    @GetMapping("/minhas")
    public List<ContaReceberResponse> listarMinhas(JwtAuthenticationToken authentication) {
        return contaReceberService.listarMinhasContas(pessoaId(authentication));
    }

    private Long pessoaId(JwtAuthenticationToken authentication) {
        Number pessoaId = authentication.getToken().getClaim("pessoaId");
        return pessoaId.longValue();
    }
}
