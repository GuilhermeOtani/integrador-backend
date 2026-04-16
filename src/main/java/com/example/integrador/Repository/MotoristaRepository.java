package com.example.integrador.Repository;

import com.example.integrador.Model.Motorista;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MotoristaRepository extends JpaRepository<Motorista, Long> {
    Motorista findByNome(String nome);
}
