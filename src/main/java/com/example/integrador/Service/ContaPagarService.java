package com.example.integrador.Service;

import com.example.integrador.Model.ContaPagar;
import com.example.integrador.Repository.ContaPagarRepository;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ContaPagarService {
    private final ContaPagarRepository contapagarRepository;

    public ContaPagarService(ContaPagarRepository contapagarRepository) {
        this.contapagarRepository = contapagarRepository;
    }

    public List<ContaPagar> listarTodasContaPagar() {
        return contapagarRepository.findAll();
    }

    public ContaPagar salvarContaPagar(ContaPagar contapagar) {
        return contapagarRepository.save(contapagar);
    }

    public ContaPagar buscarContaPagarPorId(Long id) {
        return contapagarRepository
                .findById(id).orElseThrow(() -> new RuntimeException("ContaPagar de ID " + id + " não encontrado"));
    }

    public void deletarContaPagarPorId(Long id) {
        contapagarRepository.deleteById(id);
    }

    public ContaPagar atualizarContaPagar(Long id, ContaPagar contapagar) {
        ContaPagar contapagarSalvo = buscarContaPagarPorId(id);
        BeanUtils.copyProperties(contapagar, contapagarSalvo, "id");
        return contapagarRepository.save(contapagarSalvo);
    }

}
