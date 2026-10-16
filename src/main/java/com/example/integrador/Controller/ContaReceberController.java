package com.example.integrador.Controller;

import com.example.integrador.DTO.ContaReceberResponse;
import com.example.integrador.DTO.ContaReceberRequest;
import jakarta.validation.Valid;
import com.example.integrador.Service.ContaReceberService;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

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

    @GetMapping("/buscar-contareceber/{id}")
    public ContaReceberResponse buscar(@PathVariable Long id) { return contaReceberService.buscarResposta(id); }

    @PostMapping("/salvar-contareceber")
    public ContaReceberResponse salvar(@Valid @RequestBody ContaReceberRequest entrada) {
        return contaReceberService.salvarResposta(entrada.toEntity());
    }

    @PutMapping("/atualizar-contareceber/{id}")
    public ContaReceberResponse atualizar(@PathVariable Long id, @Valid @RequestBody ContaReceberRequest entrada) {
        return contaReceberService.atualizarResposta(id, entrada.toEntity());
    }

    @DeleteMapping("/deletar-contareceber/{id}")
    public void excluir(@PathVariable Long id) { contaReceberService.deletarContaReceberPorId(id); }

    @GetMapping("/minhas")
    public List<ContaReceberResponse> listarMinhas(JwtAuthenticationToken authentication) {
        return contaReceberService.listarMinhasContas(pessoaId(authentication));
    }

    private Long pessoaId(JwtAuthenticationToken authentication) {
        Number pessoaId = authentication.getToken().getClaim("pessoaId");
        return pessoaId.longValue();
    }
}
