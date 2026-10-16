package com.example.integrador.Controller;

import com.example.integrador.DTO.ContaPagarRequest;
import com.example.integrador.DTO.ContaPagarResponse;
import com.example.integrador.Service.ContaPagarService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/contapagar")
public class ContaPagarController {

    private final ContaPagarService contaPagarService;

    public ContaPagarController(ContaPagarService contaPagarService) {
        this.contaPagarService = contaPagarService;
    }

    @GetMapping("/listar")
    public List<ContaPagarResponse> listarTodasContaPagar() {
        return contaPagarService.listarRespostas();
    }

    @PostMapping("/salvar-contapagar")
    public ContaPagarResponse salvarContaPagar(@RequestBody ContaPagarRequest contaPagar) {
        return contaPagarService.salvarResposta(contaPagar.toEntity());
    }

    @GetMapping("/buscar-contapagar/{id}")
    public ContaPagarResponse buscarContaPagarPorId(@PathVariable Long id) {
        return contaPagarService.buscarResposta(id);
    }

    @DeleteMapping("/deletar-contapagar/{id}")
    public void deletarContaPagarPorId(@PathVariable Long id) {
        contaPagarService.deletarContaPagarPorId(id);
    }

    @PutMapping("/atualizar-contapagar/{id}")
    public ContaPagarResponse atualizarContaPagar(@PathVariable Long id, @RequestBody ContaPagarRequest contaPagar) {
        return contaPagarService.atualizarResposta(id, contaPagar.toEntity());
    }
}
