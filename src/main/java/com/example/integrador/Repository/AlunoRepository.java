package com.example.integrador.Repository;

import com.example.integrador.Enum.StatusMatricula;
import com.example.integrador.Model.Aluno;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AlunoRepository extends JpaRepository<Aluno, Long> {
    Aluno findByNome(String nome);
    long countByFaculdadeIdAndStatusMatriculaAndArquivadoFalse(Long faculdadeId, StatusMatricula statusMatricula);
    List<Aluno> findAllByArquivado(boolean arquivado);
    List<Aluno> findAllByArquivadoFalseOrderByIdAsc();
}
