package com.example.integrador.Repository;

import com.example.integrador.Model.Viagem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ViagemRepository extends JpaRepository<Viagem, Long> {
    List<Viagem> findAllByMotoristaId(Long motoristaId);
}
