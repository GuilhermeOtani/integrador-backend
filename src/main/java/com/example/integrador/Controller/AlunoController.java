package com.example.integrador.Controller;


import com.example.integrador.DTO.AlunoDTO;
import com.example.integrador.DTO.ArquivamentoAlunoRequest;
import com.example.integrador.DTO.SituacaoExclusaoAlunoResponse;
import com.example.integrador.Enum.SituacaoArquivamentoAluno;
import org.springframework.http.HttpStatus;
import com.example.integrador.Model.Aluno;
import com.example.integrador.Service.AlunoService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/aluno")
public class
AlunoController {

    private final AlunoService alunoService;

    public AlunoController(AlunoService alunoService) {
        this.alunoService = alunoService;
    }

    @GetMapping("/listar")
    public List<Aluno> listarTodosAlunos(@RequestParam(defaultValue = "NAO_ARQUIVADOS") SituacaoArquivamentoAluno situacao) {
        return alunoService.listarTodosAlunos(situacao);
    }

    @PatchMapping("/{id}/arquivamento")
    public Aluno arquivar(@PathVariable Long id, @Valid @RequestBody ArquivamentoAlunoRequest request) {
        return alunoService.arquivarAluno(id, request.arquivado());
    }

    @GetMapping("/{id}/situacao-exclusao")
    public SituacaoExclusaoAlunoResponse situacaoExclusao(@PathVariable Long id) {
        return alunoService.consultarSituacaoExclusao(id);
    }

    @PostMapping("/salvar-aluno")
    public Aluno salvarAluno(@Valid @RequestBody AlunoDTO dto) {
        return alunoService.salvarAluno(dto);
    }

    @GetMapping("/buscar-aluno/{id}")
    public Aluno buscarAlunoPorId(@PathVariable Long id) {
        return alunoService.buscarAlunoPorId(id);
    }

    @DeleteMapping("/deletar-aluno/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletarAlunoPorId(@PathVariable Long id) {
        alunoService.deletarAlunoPorId(id);
    }

    @PutMapping("/atualizar-aluno/{id}")
    public Aluno atualizarAlunoPorId(@PathVariable Long id, @Valid @RequestBody AlunoDTO dto) {
        return alunoService.atualizarAluno(id, dto);
    }
}
