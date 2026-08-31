package com.example.integrador.Repository;

import com.example.integrador.Enum.StatusMatricula;
import com.example.integrador.Model.Aluno;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlunoRepository extends JpaRepository<Aluno, Long> {
    Aluno findByNome(String nome);
    long countByFaculdadeIdAndStatusMatricula(Long faculdadeId, StatusMatricula statusMatricula);
}
