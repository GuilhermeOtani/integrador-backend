package com.example.integrador.Controller;

import com.example.integrador.Model.Faculdade;
import com.example.integrador.Service.FaculdadeService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/faculdade")
@CrossOrigin(origins = "*")
public class FaculdadeController {

    private final FaculdadeService faculdadeService;

    public FaculdadeController(FaculdadeService faculdadeService) {
        this.faculdadeService = faculdadeService;
    }

    @GetMapping("/listar")
    public List<Faculdade> listarTodasFaculdades() {
        return faculdadeService.listarTodasFaculdade();
    }

    @PostMapping("/salvar-faculdade")
    public Faculdade salvarFaculdade(@RequestBody Faculdade faculdade) {
        return faculdadeService.salvarFaculdade(faculdade);
    }

    @GetMapping("/buscar-faculdade/{id}")
    public Faculdade buscarFaculdadePorId(@PathVariable Long id) {
        return faculdadeService.buscarFaculdadePorId(id);
    }

    @DeleteMapping("/deletar-faculdade/{id}")
    public void deletarFaculdadePorId(@PathVariable Long id) {
        faculdadeService.deletarFaculdadePorId(id);
    }

    @PutMapping("/atualizar-faculdade/{id}")
    public Faculdade atualizarFaculdadePorId(@PathVariable Long id, @RequestBody Faculdade faculdade) {
        return faculdadeService.atualizarFaculdade(id, faculdade);
    }
}
