package com.example.integrador.Service;

import com.example.integrador.DTO.MotoristaDTO;
import com.example.integrador.DTO.MotoristaDTO;
import com.example.integrador.Model.Motorista;
import com.example.integrador.Model.Motorista;
import com.example.integrador.Repository.MotoristaRepository;
import com.example.integrador.Repository.OnibusRepository;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MotoristaService {

    private final MotoristaRepository motoristaRepository;

    private final OnibusRepository onibusRepository;

    public MotoristaService(MotoristaRepository motoristaRepository, OnibusRepository onibusRepository) {
        this.motoristaRepository = motoristaRepository;
        this.onibusRepository = onibusRepository;
    }

    public List<Motorista> listarTodosMotoristas() {
        return motoristaRepository.findAll();
    }

    public Motorista salvarMotorista(MotoristaDTO dto) {
        Motorista motorista  = new Motorista();
        BeanUtils.copyProperties(dto, motorista, "id");
        if (dto.getOnibusId() != null) {
            motorista.setOnibus(onibusRepository.getReferenceById(dto.getOnibusId()));
        } else {
            motorista.setOnibus(null);
        }
        return motoristaRepository.save(motorista);
    }
    public Motorista buscarMotoristaPorId(Long id) {
        return motoristaRepository
                .findById(id).orElseThrow(() -> new RuntimeException("Motorista de ID " + id + " não encontrado"));
    }

    public void deletarMotoristaPorId(Long id) {
        motoristaRepository.deleteById(id);
    }

    public Motorista atualizarMotorista(Long id, MotoristaDTO dto) {
        Motorista motoristaSalvo = buscarMotoristaPorId(id);
        BeanUtils.copyProperties(dto, motoristaSalvo, "id");
        if (dto.getOnibusId() != null) {
            motoristaSalvo.setOnibus(onibusRepository.getReferenceById(dto.getOnibusId()));
        } else {
            motoristaSalvo.setOnibus(null);
        }
        return motoristaRepository.save(motoristaSalvo);
    }


    public Motorista buscarMotoristaPorNome(String nome) {
        return motoristaRepository.findByNome(nome);
    }
}
