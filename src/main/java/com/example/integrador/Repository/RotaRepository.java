package com.example.integrador.Repository;

import com.example.integrador.Model.Aluno;
import com.example.integrador.Model.Rota;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RotaRepository extends JpaRepository<Rota, Long> {
    Rota findByNome(String nome);
}
