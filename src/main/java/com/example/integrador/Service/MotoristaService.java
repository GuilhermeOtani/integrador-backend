package com.example.integrador.Service;

import com.example.integrador.DTO.MotoristaDTO;
import com.example.integrador.Enum.TipoPessoa;
import com.example.integrador.Model.Motorista;
import com.example.integrador.Repository.MotoristaRepository;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MotoristaService {

    private final MotoristaRepository motoristaRepository;
    private final ContaPagarService contaPagarService;

    public MotoristaService(MotoristaRepository motoristaRepository,
                            ContaPagarService contaPagarService) {
        this.motoristaRepository = motoristaRepository;
        this.contaPagarService = contaPagarService;
    }

    public List<Motorista> listarTodosMotoristas() {
        return motoristaRepository.findAll();
    }

    public Motorista salvarMotorista(MotoristaDTO dto) {
        Motorista motorista = new Motorista();
        BeanUtils.copyProperties(dto, motorista, "id");
        motorista.setTipoPessoa(TipoPessoa.MOTORISTA);
        Motorista motoristaSalvo = motoristaRepository.save(motorista);
        contaPagarService.gerarPrimeiraConta(motoristaSalvo);
        return motoristaSalvo;

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
        motoristaSalvo.setTipoPessoa(TipoPessoa.MOTORISTA);
        return motoristaRepository.save(motoristaSalvo);
    }


    public Motorista buscarMotoristaPorNome(String nome) {
        return motoristaRepository.findByNome(nome);
    }
}
