package com.example.integrador.Controller;

import com.example.integrador.DTO.ViagemDTO;
import com.example.integrador.Service.ViagemService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import com.example.integrador.Exception.ConflitoException;
import com.example.integrador.Exception.RecursoNaoEncontradoException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/viagem")
public class ViagemController {

    private final ViagemService viagemService;

    public ViagemController(ViagemService viagemService) {
        this.viagemService = viagemService;
    }

    @GetMapping("/listar")
    public List<ViagemDTO> listarTodasViagens() {
        return viagemService.listarTodasViagens();
    }

    @GetMapping("/minhas")
    public List<ViagemDTO> listarMinhasViagens(JwtAuthenticationToken authentication) {
        Number pessoaId = authentication.getToken().getClaim("pessoaId");
        return viagemService.listarMinhasViagens(pessoaId.longValue());
    }

    @PostMapping("/salvar-viagem")
    public ViagemDTO salvarViagem(@RequestBody ViagemDTO dto) {
        return viagemService.salvarViagem(dto);
    }

    @GetMapping("/buscar-viagem/{id}")
    public ViagemDTO buscarViagemPorId(@PathVariable Long id) {
        return viagemService.buscarViagemPorId(id);
    }

    @DeleteMapping("/deletar-viagem/{id}")
    public void deletarViagemPorId(@PathVariable Long id) {
        viagemService.deletarViagemPorId(id);
    }

    @PutMapping("/atualizar-viagem/{id}")
    public ViagemDTO atualizarViagemPorId(@PathVariable Long id, @RequestBody ViagemDTO dto) {
        return viagemService.atualizarViagem(id, dto);
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
