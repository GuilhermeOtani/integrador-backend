package com.example.integrador.Controller;

import com.example.integrador.DTO.GradeDiariaDTO;
import com.example.integrador.Service.GradeDiariaService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import com.example.integrador.Exception.ConflitoException;
import com.example.integrador.Exception.RecursoNaoEncontradoException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/grade-diaria")
public class GradeDiariaController {

    private final GradeDiariaService gradeDiariaService;

    public GradeDiariaController(GradeDiariaService gradeDiariaService) {
        this.gradeDiariaService = gradeDiariaService;
    }

    @GetMapping("/listar")
    public List<GradeDiariaDTO> listarTodasGrades() {
        return gradeDiariaService.listarTodasGrades();
    }

    @PostMapping("/salvar-grade")
    public GradeDiariaDTO salvarGrade(@RequestBody GradeDiariaDTO dto) {
        return gradeDiariaService.salvarGrade(dto);
    }

    @GetMapping("/buscar-grade/{id}")
    public GradeDiariaDTO buscarGradePorId(@PathVariable Long id) {
        return gradeDiariaService.buscarGradePorId(id);
    }

    @DeleteMapping("/deletar-grade/{id}")
    public void deletarGradePorId(@PathVariable Long id) {
        gradeDiariaService.deletarGradePorId(id);
    }

    @PutMapping("/atualizar-grade/{id}")
    public GradeDiariaDTO atualizarGradePorId(@PathVariable Long id, @RequestBody GradeDiariaDTO dto) {
        return gradeDiariaService.atualizarGrade(id, dto);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<String> handleRuntimeException(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }

    @ExceptionHandler(ConflitoException.class)
    public ProblemDetail conflito(ConflitoException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ProblemDetail naoEncontrado(RecursoNaoEncontradoException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }
}
