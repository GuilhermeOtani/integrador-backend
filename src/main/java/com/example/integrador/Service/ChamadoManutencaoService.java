package com.example.integrador.Service;

import com.example.integrador.Model.ChamadoManutencao;
import com.example.integrador.Repository.ChamadoManutencaoRepository;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ChamadoManutencaoService {

    private final ChamadoManutencaoRepository chamadomanutencaoRepository;

    public ChamadoManutencaoService(ChamadoManutencaoRepository chamadomanutencaoRepository) {
        this.chamadomanutencaoRepository = chamadomanutencaoRepository;
    }

    public List<ChamadoManutencao> listarTodasChamadoManutencao() {
        return chamadomanutencaoRepository.findAll();
    }

    public ChamadoManutencao salvarChamadoManutencao(ChamadoManutencao chamadomanutencao) {
        return chamadomanutencaoRepository.save(chamadomanutencao);
    }

    public ChamadoManutencao buscarChamadoManutencaoPorId(Long id) {
        return chamadomanutencaoRepository
                .findById(id).orElseThrow(() -> new RuntimeException("ChamadoManutencao de ID " + id + " não encontrado"));
    }

    public void deletarChamadoManutencaoPorId(Long id) {
        chamadomanutencaoRepository.deleteById(id);
    }

    public ChamadoManutencao atualizarChamadoManutencao(Long id, ChamadoManutencao chamadomanutencao) {
        ChamadoManutencao chamadomanutencaoSalvo = buscarChamadoManutencaoPorId(id);
        BeanUtils.copyProperties(chamadomanutencao, chamadomanutencaoSalvo, "id");
        return chamadomanutencaoRepository.save(chamadomanutencaoSalvo);
    }

}
