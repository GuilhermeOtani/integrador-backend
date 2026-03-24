package com.example.integrador.Repository;

import com.example.integrador.Model.ChamadoManutencao;
import com.example.integrador.Model.Viagem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ViagemRepository extends JpaRepository<Viagem, Long> {
}
