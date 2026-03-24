package com.example.integrador.Service;

import com.example.integrador.Model.ContaReceber;
import com.example.integrador.Repository.ContaReceberRepository;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ContaReceberService {
    private final ContaReceberRepository contareceberRepository;

    public ContaReceberService(ContaReceberRepository contareceberRepository) {
        this.contareceberRepository = contareceberRepository;
    }

    public List<ContaReceber> listarTodasContaReceber() {
        return contareceberRepository.findAll();
    }

    public ContaReceber salvarContaReceber(ContaReceber contareceber) {
        return contareceberRepository.save(contareceber);
    }

    public ContaReceber buscarContaReceberPorId(Long id) {
        return contareceberRepository
                .findById(id).orElseThrow(() -> new RuntimeException("ContaReceber de ID " + id + " não encontrado"));
    }

    public void deletarContaReceberPorId(Long id) {
        contareceberRepository.deleteById(id);
    }

    public ContaReceber atualizarContaReceber(Long id, ContaReceber contareceber) {
        ContaReceber contareceberSalvo = buscarContaReceberPorId(id);
        BeanUtils.copyProperties(contareceber, contareceberSalvo, "id");
        return contareceberRepository.save(contareceberSalvo);
    }

}
