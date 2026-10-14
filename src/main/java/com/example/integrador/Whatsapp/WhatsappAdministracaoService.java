package com.example.integrador.Whatsapp;

import com.example.integrador.Whatsapp.WhatsappDto.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class WhatsappAdministracaoService {
    private final WhatsappRepos repos;
    private final WhatsappDisponibilidadeService disponibilidade;
    private final TransactionTemplate tx;
    public WhatsappAdministracaoService(WhatsappRepos repos, WhatsappDisponibilidadeService disponibilidade, TransactionTemplate tx) {
        this.repos = repos; this.disponibilidade = disponibilidade; this.tx = tx;
    }
    @PreAuthorize("hasRole('ADMIN')")
    public ConfiguracaoResponse consultar() { return disponibilidade.consultar(); }
    @PreAuthorize("hasRole('ADMIN')")
    public ConfiguracaoResponse atualizar(ConfiguracaoRequest request) {
        if (Boolean.TRUE.equals(request.automatico())) disponibilidade.verificarEnvio();
        tx.executeWithoutResult(s -> repos.bloquearConfiguracao().setAutomatico(Boolean.TRUE.equals(request.automatico())));
        return disponibilidade.consultar();
    }
}
