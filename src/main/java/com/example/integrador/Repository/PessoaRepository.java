package com.example.integrador.Repository;

import com.example.integrador.Model.Motorista;
import com.example.integrador.Model.Pessoa;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PessoaRepository extends JpaRepository<Pessoa, Long> {
    Pessoa findByNome(String nome);

}
