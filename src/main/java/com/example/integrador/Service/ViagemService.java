package com.example.integrador.Service;

import com.example.integrador.Model.Viagem;
import com.example.integrador.Repository.ViagemRepository;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ViagemService {

    private final ViagemRepository viagemRepository;

    public ViagemService(ViagemRepository viagemRepository) {
        this.viagemRepository = viagemRepository;
    }

    public List<Viagem> listarTodasViagem() {
        return viagemRepository.findAll();
    }

    public Viagem salvarViagem(Viagem viagem) {
        return viagemRepository.save(viagem);
    }

    public Viagem buscarViagemPorId(Long id) {
        return viagemRepository
                .findById(id).orElseThrow(() -> new RuntimeException("Viagem de ID " + id + " não encontrado"));
    }

    public void deletarViagemPorId(Long id) {
        viagemRepository.deleteById(id);
    }

    public Viagem atualizarViagem(Long id, Viagem viagem) {
        Viagem viagemSalvo = buscarViagemPorId(id);
        BeanUtils.copyProperties(viagem, viagemSalvo, "id");
        return viagemRepository.save(viagemSalvo);
    }
}
