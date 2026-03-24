package com.example.integrador.Repository;

import com.example.integrador.Model.Aluno;
import com.example.integrador.Model.ChamadoManutencao;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChamadoManutencaoRepository extends JpaRepository<ChamadoManutencao, Long> {
}
