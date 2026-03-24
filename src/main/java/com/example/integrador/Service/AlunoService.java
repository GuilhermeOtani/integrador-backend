package com.example.integrador.Service;


import com.example.integrador.Model.Aluno;
import com.example.integrador.Repository.AlunoRepository;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AlunoService {

    private final AlunoRepository alunoRepository;

    public AlunoService(AlunoRepository alunoRepository) {
        this.alunoRepository = alunoRepository;
    }

    public List<Aluno> listarTodosAlunos() {
        return alunoRepository.findAll();
    }

    public Aluno salvarAluno(Aluno aluno) {
        return alunoRepository.save(aluno);
    }

    public Aluno buscarAlunoPorId(Long id) {
        return alunoRepository
                .findById(id).orElseThrow(() -> new RuntimeException("Aluno de ID " + id + " não encontrado"));
    }

    public void deletarAlunoPorId(Long id) {
        alunoRepository.deleteById(id);
    }

    public Aluno atualizarAluno(Long id, Aluno aluno) {
        Aluno alunoSalvo = buscarAlunoPorId(id);
        BeanUtils.copyProperties(aluno, alunoSalvo, "id");
        return alunoRepository.save(alunoSalvo);
    }

    public Aluno buscarAlunoPorNome(String nome) {
        return alunoRepository.findByNome(nome);
    }

}
