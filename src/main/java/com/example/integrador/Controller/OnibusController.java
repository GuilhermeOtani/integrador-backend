package com.example.integrador.Controller;

import com.example.integrador.Model.Onibus;
import com.example.integrador.Service.OnibusService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/onibus")
@CrossOrigin(origins = "*")
public class OnibusController {

    private final OnibusService onibusService;

    public OnibusController(OnibusService onibusService) {
        this.onibusService = onibusService;
    }

    @GetMapping("/listar")
    public List<Onibus> listarTodasOnibuss() {
        return onibusService.listarTodasOnibus();
    }

    @PostMapping("/salvar-onibus")
    public Onibus salvarOnibus(@RequestBody Onibus onibus) {
        return onibusService.salvarOnibus(onibus);
    }

    @GetMapping("/buscar-onibus/{id}")
    public Onibus buscarOnibusPorId(@PathVariable Long id) {
        return onibusService.buscarOnibusPorId(id);
    }

    @DeleteMapping("/deletar-onibus/{id}")
    public void deletarOnibusPorId(@PathVariable Long id) {
        onibusService.deletarOnibusPorId(id);
    }

    @PutMapping("/atualizar-onibus/{id}")
    public Onibus atualizarOnibusPorId(@PathVariable Long id, @RequestBody Onibus onibus) {
        return onibusService.atualizarOnibus(id, onibus);
    }
}
