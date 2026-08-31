package com.example.integrador.Controller;

import com.example.integrador.Model.Rota;
import com.example.integrador.Service.RotaService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/rota")
@CrossOrigin(origins = "*")
public class RotaController {

    private final RotaService rotaService;

    public RotaController(RotaService rotaService) {
        this.rotaService = rotaService;
    }

    @GetMapping("/listar")
    public List<Rota> listarTodasRotas() {
        return rotaService.listarTodasRota();
    }

    @PostMapping("/salvar-rota")
    public Rota salvarRota(@RequestBody Rota rota) {
        return rotaService.salvarRota(rota);
    }

    @GetMapping("/buscar-rota/{id}")
    public Rota buscarRotaPorId(@PathVariable Long id) {
        return rotaService.buscarRotaPorId(id);
    }

    @DeleteMapping("/deletar-rota/{id}")
    public void deletarRotaPorId(@PathVariable Long id) {
        rotaService.deletarRotaPorId(id);
    }

    @PutMapping("/{id}")
    public Rota atualizarRotaPorId(@PathVariable Long id, @RequestBody Rota rota) {
        return rotaService.atualizarRota(id, rota);
    }
}