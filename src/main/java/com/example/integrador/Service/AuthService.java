package com.example.integrador.Service;

import com.example.integrador.DTO.LoginRequest;
import com.example.integrador.DTO.LoginResponse;
import com.example.integrador.DTO.MeResponse;
import com.example.integrador.DTO.UsuarioResponse;
import com.example.integrador.Model.Usuario;
import com.example.integrador.Repository.UsuarioRepository;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;
    private final UsuarioRepository usuarioRepository;

    public AuthService(AuthenticationManager authenticationManager,
                       TokenService tokenService,
                       UsuarioRepository usuarioRepository) {
        this.authenticationManager = authenticationManager;
        this.tokenService = tokenService;
        this.usuarioRepository = usuarioRepository;
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(
                        request.email().trim(), request.senha()));
        Usuario usuario = (Usuario) authentication.getPrincipal();
        TokenService.TokenEmitido token = tokenService.emitir(usuario);
        return new LoginResponse(
                token.valor(), "Bearer", token.expiresIn(), UsuarioResponse.fromEntity(usuario));
    }

    @Transactional(readOnly = true)
    public MeResponse buscarPerfil(Long usuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new org.springframework.security.core.userdetails.UsernameNotFoundException(
                        "Usuário autenticado não encontrado"));
        return MeResponse.fromEntity(usuario);
    }
}
