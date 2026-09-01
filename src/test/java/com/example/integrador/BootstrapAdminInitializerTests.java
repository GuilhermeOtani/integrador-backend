package com.example.integrador;

import com.example.integrador.Security.BootstrapAdminInitializer;
import com.example.integrador.Service.UsuarioService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BootstrapAdminInitializerTests {

    @Test
    void criaAdministradorInicialUmaUnicaVez() {
        UsuarioService service = mock(UsuarioService.class);
        when(service.existeAdmin()).thenReturn(false);
        BootstrapAdminInitializer initializer = new BootstrapAdminInitializer(
                service, true, "Admin Inicial", "admin@email.com", "SenhaInicial123");

        initializer.run(new DefaultApplicationArguments());

        verify(service).criarAdminInicial("Admin Inicial", "admin@email.com", "SenhaInicial123");
    }

    @Test
    void naoDuplicaAdministradorExistente() {
        UsuarioService service = mock(UsuarioService.class);
        when(service.existeAdmin()).thenReturn(true);
        BootstrapAdminInitializer initializer = new BootstrapAdminInitializer(
                service, true, "", "", "");

        initializer.run(new DefaultApplicationArguments());

        verify(service, never()).criarAdminInicial(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void falhaComMensagemClaraQuandoConfiguracaoEstaAusente() {
        UsuarioService service = mock(UsuarioService.class);
        when(service.existeAdmin()).thenReturn(false);
        BootstrapAdminInitializer initializer = new BootstrapAdminInitializer(
                service, true, "", "", "");

        assertThatThrownBy(() -> initializer.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BOOTSTRAP_ADMIN_NAME");
    }
}
