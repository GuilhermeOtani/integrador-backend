package com.example.integrador.Controller;


import com.example.integrador.Model.Aluno;
import com.example.integrador.Service.AlunoService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/aluno")
@CrossOrigin(origins = "*")
public class
AlunoController {

    private final AlunoService alunoService;

    public AlunoController(AlunoService alunoService) {
        this.alunoService = alunoService;
    }

    @GetMapping("/listar")
    public List<Aluno> listarTodosAlunos() {
        return alunoService.listarTodosAlunos();
    }

    @PostMapping("/salvar-aluno")
    public Aluno salvarAluno(@RequestBody Aluno aluno) {
        return alunoService.salvarAluno(aluno);
    }

    @GetMapping("/buscar-aluno/{id}")
    public Aluno buscarAlunoPorId(@PathVariable Long id) {
        return alunoService.buscarAlunoPorId(id);
    }

    @DeleteMapping("/deletar-aluno/{id}")
    public void deletarAlunoPorId(@PathVariable Long id) {
        alunoService.deletarAlunoPorId(id);
    }

    @PutMapping("/atualizar-aluno/{id}")
    public Aluno atualizarAlunoPorId(@PathVariable Long id, @RequestBody Aluno aluno) {
        return alunoService.atualizarAluno(id, aluno);
    }
}
