package com.example.integrador.Service;

import com.example.integrador.Exception.ConflitoException;
import com.example.integrador.Repository.ChamadoManutencaoRepository;
import org.springframework.stereotype.Service;

@Service
public class ProtecaoHistoricoManutencaoService {
    private final ChamadoManutencaoRepository repository;

    public ProtecaoHistoricoManutencaoService(ChamadoManutencaoRepository repository) {
        this.repository = repository;
    }

    public void verificarPessoa(Long id) {
        if (repository.existsBySolicitanteId(id)) {
            throw new ConflitoException("Não é possível excluir uma pessoa que possui chamados de manutenção");
        }
    }

    public void verificarOnibus(Long id) {
        if (repository.existsByOnibusId(id)) {
            throw new ConflitoException("Não é possível excluir um ônibus que possui chamados de manutenção");
        }
    }
}
