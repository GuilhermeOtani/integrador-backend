package com.example.integrador.Service;

import com.example.integrador.Model.Aluno;
import com.example.integrador.Model.Rota;
import com.example.integrador.Repository.AlunoRepository;
import com.example.integrador.Repository.RotaRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RotaService {

    private final RotaRepository rotaRepository;


    public RotaService(RotaRepository rotaRepository) {
        this.rotaRepository = rotaRepository;

    }

    public List<Rota> listarTodasRota() {
        return rotaRepository.findAll();
    }

    @Transactional
    public Rota salvarRota(Rota rota) {
        return rotaRepository.save(rota);
    }

    public Rota buscarRotaPorId(Long id) {
        return rotaRepository
                .findById(id).orElseThrow(() -> new RuntimeException("Rota de ID " + id + " não encontrado"));
    }

    public void deletarRotaPorId(Long id) {
        Rota rota = rotaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Rota não encontrada"));

        rotaRepository.delete(rota);
    }

    @Transactional
    public Rota atualizarRota(Long id, Rota rotaAtualizada) {
        Rota rotaExistente = rotaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Rota não encontrada"));

        rotaExistente.setNome(rotaAtualizada.getNome());
        rotaExistente.setDescricao(rotaAtualizada.getDescricao());
        rotaExistente.setFaculdade(rotaAtualizada.getFaculdade());

        if (rotaExistente.getPontosEmbarque() == null) {
            rotaExistente.setPontosEmbarque(new java.util.ArrayList<>());
        } else {
            rotaExistente.getPontosEmbarque().clear();
        }

        if (rotaAtualizada.getPontosEmbarque() != null) {
            rotaExistente.getPontosEmbarque().addAll(rotaAtualizada.getPontosEmbarque());
        }

        return rotaRepository.save(rotaExistente);
    }
    public Rota buscarRotaPorNome(String nome) {
        return rotaRepository.findByNome(nome);
    }
}
