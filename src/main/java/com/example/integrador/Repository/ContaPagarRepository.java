package com.example.integrador.Repository;

import com.example.integrador.Model.Aluno;
import com.example.integrador.Model.ContaPagar;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContaPagarRepository extends JpaRepository<ContaPagar, Long> {
}
