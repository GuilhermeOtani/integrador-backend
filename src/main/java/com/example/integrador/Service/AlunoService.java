package com.example.integrador.Service;


import com.example.integrador.Model.Aluno;
import com.example.integrador.Repository.AlunoRepository;
import com.example.integrador.Repository.FaculdadeRepository;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import com.example.integrador.DTO.AlunoDTO;

import java.util.List;

@Service
public class AlunoService {


    private final AlunoRepository alunoRepository;

    private final FaculdadeRepository faculdadeRepository;

    public AlunoService(AlunoRepository alunoRepository, FaculdadeRepository faculdadeRepository) {
        this.alunoRepository = alunoRepository;
        this.faculdadeRepository = faculdadeRepository;
    }

    public List<Aluno> listarTodosAlunos() {
        return alunoRepository.findAll();
    }

    public Aluno salvarAluno(AlunoDTO dto) {
        Aluno aluno = new Aluno();
        BeanUtils.copyProperties(dto, aluno, "id");
        if (dto.getFaculdadeId() != null) {
            aluno.setFaculdade(faculdadeRepository.getReferenceById(dto.getFaculdadeId()));
        } else {
            aluno.setFaculdade(null);
        }
        return alunoRepository.save(aluno);
    }

    public Aluno buscarAlunoPorId(Long id) {
        return alunoRepository
                .findById(id).orElseThrow(() -> new RuntimeException("Aluno de ID " + id + " não encontrado"));
    }

    public void deletarAlunoPorId(Long id) {
        alunoRepository.deleteById(id);
    }

    public Aluno atualizarAluno(Long id, AlunoDTO dto) {
        Aluno alunoSalvo = buscarAlunoPorId(id);
        BeanUtils.copyProperties(dto, alunoSalvo, "id");
        if (dto.getFaculdadeId() != null) {
            alunoSalvo.setFaculdade(faculdadeRepository.getReferenceById(dto.getFaculdadeId()));
        } else {
            alunoSalvo.setFaculdade(null);
        }
        return alunoRepository.save(alunoSalvo);
    }

    public Aluno buscarAlunoPorNome(String nome) {
        return alunoRepository.findByNome(nome);
    }

}
