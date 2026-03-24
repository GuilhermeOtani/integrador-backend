package com.example.integrador.Service;

import com.example.integrador.Model.Rota;
import com.example.integrador.Repository.RotaRepository;
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

    public Rota salvarRota(Rota rota) {
        return rotaRepository.save(rota);
    }

    public Rota buscarRotaPorId(Long id) {
        return rotaRepository
                .findById(id).orElseThrow(() -> new RuntimeException("Rota de ID " + id + " não encontrado"));
    }

    public void deletarRotaPorId(Long id) {
        rotaRepository.deleteById(id);
    }

    public Rota atualizarRota(Long id, Rota rota) {
        Rota rotaSalvo = buscarRotaPorId(id);
        BeanUtils.copyProperties(rota, rotaSalvo, "id");
        return rotaRepository.save(rotaSalvo);
    }

    public Rota buscarRotaPorNome(String nome) {
        return rotaRepository.findByNome(nome);
    }
}
