package com.example.integrador.Service;

import com.example.integrador.Enum.StatusOnibus;
import com.example.integrador.Exception.ConflitoException;
import com.example.integrador.Exception.RecursoNaoEncontradoException;
import com.example.integrador.Model.Onibus;
import com.example.integrador.Repository.OnibusRepository;
import org.springframework.stereotype.Service;
import java.util.Objects;

@Service
public class DisponibilidadeOnibusService {
    private final OnibusRepository repository;

    public DisponibilidadeOnibusService(OnibusRepository repository) { this.repository = repository; }

    public Onibus resolverAtribuicao(Long novoId, Long anteriorId) {
        if (novoId == null) return null;
        Onibus onibus = repository.findById(novoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Ônibus não encontrado"));
        if (!Objects.equals(novoId, anteriorId) && onibus.getStatusOnibus() != StatusOnibus.OPERANDO) {
            throw new ConflitoException("Somente ônibus OPERANDO pode ser atribuído à viagem. Ônibus "
                    + novoId + ": " + onibus.getStatusOnibus());
        }
        return onibus;
    }
}
