package com.example.integrador.Whatsapp;

import com.example.integrador.Model.Usuario;
import com.example.integrador.Repository.UsuarioRepository;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class WhatsappIdentidade {
    private final UsuarioRepository usuarios;
    public WhatsappIdentidade(UsuarioRepository usuarios) { this.usuarios = usuarios; }
    public Usuario atual() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwt)) throw new AuthenticationCredentialsNotFoundException("JWT obrigatório");
        return usuarios.findById(Long.valueOf(jwt.getToken().getSubject()))
                .filter(Usuario::isAtivo)
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("Conta inexistente ou inativa"));
    }
}
