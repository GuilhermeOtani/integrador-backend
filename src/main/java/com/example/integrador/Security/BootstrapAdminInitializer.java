package com.example.integrador.Security;

import com.example.integrador.Service.UsuarioService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class BootstrapAdminInitializer implements ApplicationRunner {
    private final UsuarioService usuarioService;
    private final boolean enabled;
    private final String nome;
    private final String email;
    private final String senha;

    public BootstrapAdminInitializer(
            UsuarioService usuarioService,
            @Value("${app.security.bootstrap-admin.enabled:true}") boolean enabled,
            @Value("${app.security.bootstrap-admin.name:}") String nome,
            @Value("${app.security.bootstrap-admin.email:}") String email,
            @Value("${app.security.bootstrap-admin.password:}") String senha) {
        this.usuarioService = usuarioService;
        this.enabled = enabled;
        this.nome = nome;
        this.email = email;
        this.senha = senha;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled || usuarioService.existeAdmin()) {
            return;
        }

        if (nome.isBlank() || email.isBlank() || senha.isBlank()) {
            throw new IllegalStateException(
                    "Nenhum ADMIN existe. Defina BOOTSTRAP_ADMIN_NAME, BOOTSTRAP_ADMIN_EMAIL "
                            + "e BOOTSTRAP_ADMIN_PASSWORD para criar o administrador inicial.");
        }
        if (senha.length() < 8 || senha.length() > 72) {
            throw new IllegalStateException("BOOTSTRAP_ADMIN_PASSWORD deve ter entre 8 e 72 caracteres");
        }

        usuarioService.criarAdminInicial(nome, email, senha);
    }
}
