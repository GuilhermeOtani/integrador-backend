package com.example.integrador.Email;

import java.util.UUID;

public interface EmailGateway {
    void enviar(UUID entregaId, String destinatario, EmailDto.Conteudo conteudo);
}
