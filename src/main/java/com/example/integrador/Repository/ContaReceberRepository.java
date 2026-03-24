package com.example.integrador.Repository;

import com.example.integrador.Model.Aluno;
import com.example.integrador.Model.ContaReceber;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContaReceberRepository extends JpaRepository<ContaReceber, Long> {
}
