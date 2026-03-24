package com.example.integrador.Repository;

import com.example.integrador.Model.Aluno;
import com.example.integrador.Model.Pagamento;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PagamentoRepository extends JpaRepository<Pagamento, Long> {
}
