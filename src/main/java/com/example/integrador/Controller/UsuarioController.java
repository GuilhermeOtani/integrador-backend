package com.example.integrador.Controller;

import com.example.integrador.DTO.CriarAdminRequest;
import com.example.integrador.DTO.CriarUsuarioRequest;
import com.example.integrador.DTO.AtualizarEmailUsuarioRequest;
import com.example.integrador.DTO.AtualizarStatusUsuarioRequest;
import com.example.integrador.DTO.PessoaDisponivelResponse;
import com.example.integrador.DTO.RedefinirSenhaUsuarioRequest;
import com.example.integrador.DTO.UsuarioResponse;
import com.example.integrador.Service.UsuarioService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
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

    @PatchMapping("/{id}")
    public UsuarioResponse atualizarEmail(@PathVariable Long id,
                                          @Valid @RequestBody AtualizarEmailUsuarioRequest request) {
        return usuarioService.atualizarEmail(id, request);
    }

    @PutMapping("/{id}/senha")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void redefinirSenha(@PathVariable Long id,
                               @Valid @RequestBody RedefinirSenhaUsuarioRequest request) {
        usuarioService.redefinirSenha(id, request);
    }

    @PatchMapping("/{id}/status")
    public UsuarioResponse atualizarStatus(@PathVariable Long id,
                                           @Valid @RequestBody AtualizarStatusUsuarioRequest request,
                                           JwtAuthenticationToken authentication) {
        return usuarioService.atualizarStatus(id, request, usuarioAutenticadoId(authentication));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluirAcesso(@PathVariable Long id, JwtAuthenticationToken authentication) {
        usuarioService.excluirAcesso(id, usuarioAutenticadoId(authentication));
    }

    private Long usuarioAutenticadoId(JwtAuthenticationToken authentication) {
        return Long.valueOf(authentication.getName());
    }
}
