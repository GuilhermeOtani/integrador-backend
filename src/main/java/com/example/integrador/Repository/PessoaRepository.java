package com.example.integrador.Repository;

import com.example.integrador.Model.Pessoa;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PessoaRepository extends JpaRepository<Pessoa, Long> {
    Pessoa findByNome(String nome);

    List<Pessoa> findAllByOrderByNomeAsc();

}
