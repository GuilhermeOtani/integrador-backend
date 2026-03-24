package com.example.integrador.Service;

import com.example.integrador.Model.Faculdade;
import com.example.integrador.Repository.FaculdadeRepository;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FaculdadeService {

    private final FaculdadeRepository faculdadeRepository;

    public FaculdadeService(FaculdadeRepository faculdadeRepository) {
        this.faculdadeRepository = faculdadeRepository;
    }

    public List<Faculdade> listarTodasFaculdade() {
        return faculdadeRepository.findAll();
    }

    public Faculdade salvarFaculdade(Faculdade faculdade) {
        return faculdadeRepository.save(faculdade);
    }

    public Faculdade buscarFaculdadePorId(Long id) {
        return faculdadeRepository
                .findById(id).orElseThrow(() -> new RuntimeException("Faculdade de ID " + id + " não encontrado"));
    }

    public void deletarFaculdadePorId(Long id) {
        faculdadeRepository.deleteById(id);
    }

    public Faculdade atualizarFaculdade(Long id, Faculdade faculdade) {
        Faculdade faculdadeSalvo = buscarFaculdadePorId(id);
        BeanUtils.copyProperties(faculdade, faculdadeSalvo, "id");
        return faculdadeRepository.save(faculdadeSalvo);
    }

    public Faculdade buscarFaculdadePorNome(String nome) {
        return faculdadeRepository.findByNome(nome);
    }
}
