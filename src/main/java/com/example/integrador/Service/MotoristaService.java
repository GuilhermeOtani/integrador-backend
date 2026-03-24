package com.example.integrador.Service;

import com.example.integrador.Model.Motorista;
import com.example.integrador.Repository.MotoristaRepository;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MotoristaService {

    private final MotoristaRepository motoristaRepository;

    public MotoristaService(MotoristaRepository motoristaRepository) {
        this.motoristaRepository = motoristaRepository;
    }

    public List<Motorista> listarTodasMotorista() {
        return motoristaRepository.findAll();
    }

    public Motorista salvarMotorista(Motorista motorista) {
        return motoristaRepository.save(motorista);
    }

    public Motorista buscarMotoristaPorId(Long id) {
        return motoristaRepository
                .findById(id).orElseThrow(() -> new RuntimeException("Motorista de ID " + id + " não encontrado"));
    }

    public void deletarMotoristaPorId(Long id) {
        motoristaRepository.deleteById(id);
    }

    public Motorista atualizarMotorista(Long id, Motorista motorista) {
        Motorista motoristaSalvo = buscarMotoristaPorId(id);
        BeanUtils.copyProperties(motorista, motoristaSalvo, "id");
        return motoristaRepository.save(motoristaSalvo);
    }

    public Motorista buscarMotoristaPorNome(String nome) {
        return motoristaRepository.findByNome(nome);
    }
}
