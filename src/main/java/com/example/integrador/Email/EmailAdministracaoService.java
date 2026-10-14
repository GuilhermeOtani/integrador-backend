package com.example.integrador.Email;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@PreAuthorize("hasRole('ADMIN')")
public class EmailAdministracaoService {
    private final EmailRepository repository;
    private final EmailDisponibilidadeService disponibilidade;
    private final EmailProperties properties;
    public EmailAdministracaoService(EmailRepository repository, EmailDisponibilidadeService disponibilidade,
                                    EmailProperties properties) {
        this.repository = repository;
        this.disponibilidade = disponibilidade;
        this.properties = properties;
    }
    @Transactional(readOnly = true)
    public EmailDto.ConfiguracaoResponse consultar() {
        List<String> impedimentos = disponibilidade.impedimentos();
        return new EmailDto.ConfiguracaoResponse(properties.isEnabled(), repository.configuracao().isAutomatico(),
                !properties.isProduction(), impedimentos.isEmpty(), "15:00", "America/Sao_Paulo", impedimentos);
    }
    @Transactional
    public EmailDto.ConfiguracaoResponse atualizar(boolean automatico) {
        if (automatico) disponibilidade.exigirDisponivel();
        repository.bloquearConfiguracao().setAutomatico(automatico);
        return consultar();
    }
}
