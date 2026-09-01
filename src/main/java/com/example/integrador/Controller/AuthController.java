package com.example.integrador.Controller;

import com.example.integrador.DTO.LoginRequest;
import com.example.integrador.DTO.LoginResponse;
import com.example.integrador.DTO.MeResponse;
import com.example.integrador.Service.AuthService;
import jakarta.validation.Valid;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    public MeResponse me(JwtAuthenticationToken authentication) {
        return authService.buscarPerfil(Long.valueOf(authentication.getName()));
    }
}
