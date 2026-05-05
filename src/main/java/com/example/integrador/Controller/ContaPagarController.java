package com.example.integrador.Controller;

import com.example.integrador.Model.ContaPagar;
import com.example.integrador.Service.ContaPagarService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/contapagar")
@CrossOrigin(origins = "*")
public class ContaPagarController {

    private final ContaPagarService contaPagarService;

    public ContaPagarController(ContaPagarService contaPagarService) {
        this.contaPagarService = contaPagarService;
    }

    @GetMapping("/listar")
    public List<ContaPagar> listarTodasContaPagar() {
        return contaPagarService.listarTodasContaPagar();
    }

    @PostMapping("/salvar-contapagar")
    public ContaPagar salvarContaPagar(@RequestBody ContaPagar contaPagar) {
        return contaPagarService.salvarContaPagar(contaPagar);
    }

    @GetMapping("/buscar-contapagar/{id}")
    public ContaPagar buscarContaPagarPorId(@PathVariable Long id) {
        return contaPagarService.buscarContaPagarPorId(id);
    }

    @DeleteMapping("/deletar-contapagar/{id}")
    public void deletarContaPagarPorId(@PathVariable Long id) {
        contaPagarService.deletarContaPagarPorId(id);
    }

    @PutMapping("/atualizar-contapagar/{id}")
    public ContaPagar atualizarContaPagar(@PathVariable Long id, @RequestBody ContaPagar contaPagar) {
        return contaPagarService.atualizarContaPagar(id, contaPagar);
    }
}