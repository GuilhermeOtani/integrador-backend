package com.example.integrador.Service;

import com.example.integrador.Model.Onibus;
import com.example.integrador.Repository.OnibusRepository;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OnibusService {

    private final OnibusRepository onibusRepository;

    public OnibusService(OnibusRepository onibusRepository) {
        this.onibusRepository = onibusRepository;
    }

    public List<Onibus> listarTodasOnibus() {
        return onibusRepository.findAll();
    }

    public Onibus salvarOnibus(Onibus onibus) {
        return onibusRepository.save(onibus);
    }

    public Onibus buscarOnibusPorId(Long id) {
        return onibusRepository
                .findById(id).orElseThrow(() -> new RuntimeException("Onibus de ID " + id + " não encontrado"));
    }

    public void deletarOnibusPorId(Long id) {
        onibusRepository.deleteById(id);
    }

    public Onibus atualizarOnibus(Long id, Onibus onibus) {
        Onibus onibusSalvo = buscarOnibusPorId(id);
        BeanUtils.copyProperties(onibus, onibusSalvo, "id");
        return onibusRepository.save(onibusSalvo);
    }

}
