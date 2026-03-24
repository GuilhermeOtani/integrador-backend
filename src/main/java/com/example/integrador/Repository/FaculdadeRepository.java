package com.example.integrador.Repository;

import com.example.integrador.Model.Aluno;
import com.example.integrador.Model.Faculdade;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FaculdadeRepository extends JpaRepository<Faculdade, Long> {
    Faculdade findByNome(String nome);
}
