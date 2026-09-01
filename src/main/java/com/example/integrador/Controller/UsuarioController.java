package com.example.integrador.Controller;

import com.example.integrador.DTO.CriarAdminRequest;
import com.example.integrador.DTO.CriarUsuarioRequest;
import com.example.integrador.DTO.PessoaDisponivelResponse;
import com.example.integrador.DTO.UsuarioResponse;
import com.example.integrador.Service.UsuarioService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/usuarios")
@PreAuthorize("hasRole('ADMIN')")
public class UsuarioController {
    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @GetMapping
    public List<UsuarioResponse> listar() {
        return usuarioService.listarTodos();
    }

    @GetMapping("/pessoas-sem-usuario")
    public List<PessoaDisponivelResponse> listarPessoasSemUsuario() {
        return usuarioService.listarPessoasSemUsuario();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioResponse criar(@Valid @RequestBody CriarUsuarioRequest request) {
        return usuarioService.criarUsuario(request);
    }

    @PostMapping("/admin")
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioResponse criarAdmin(@Valid @RequestBody CriarAdminRequest request) {
        return usuarioService.criarAdmin(request);
    }
}
