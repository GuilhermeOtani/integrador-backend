package com.example.integrador.Service;

import com.example.integrador.Model.PontoEmbarque;
import com.example.integrador.Repository.PontoEmbarqueRepository;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PontoEmbarqueService {

    private final PontoEmbarqueRepository pontoembarqueRepository;

    public PontoEmbarqueService(PontoEmbarqueRepository pontoembarqueRepository) {
        this.pontoembarqueRepository = pontoembarqueRepository;
    }

    public List<PontoEmbarque> listarTodosPontoEmbarque() {
        return pontoembarqueRepository.findAll();
    }

    public PontoEmbarque salvarPontoEmbarque(PontoEmbarque pontoembarque) {
        return pontoembarqueRepository.save(pontoembarque);
    }

    public PontoEmbarque buscarPontoEmbarquePorId(Long id) {
        return pontoembarqueRepository
                .findById(id).orElseThrow(() -> new RuntimeException("PontoEmbarque de ID " + id + " não encontrado"));
    }

    public void deletarPontoEmbarquePorId(Long id) {
        pontoembarqueRepository.deleteById(id);
    }

    public PontoEmbarque atualizarPontoEmbarque(Long id, PontoEmbarque pontoembarque) {
        PontoEmbarque pontoembarqueSalvo = buscarPontoEmbarquePorId(id);
        BeanUtils.copyProperties(pontoembarque, pontoembarqueSalvo, "id");
        return pontoembarqueRepository.save(pontoembarqueSalvo);

    }
}
