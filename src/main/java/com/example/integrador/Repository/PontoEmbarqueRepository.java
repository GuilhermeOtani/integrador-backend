package com.example.integrador.Repository;

import com.example.integrador.Model.Aluno;
import com.example.integrador.Model.PontoEmbarque;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PontoEmbarqueRepository extends JpaRepository<PontoEmbarque, Long> {
}
