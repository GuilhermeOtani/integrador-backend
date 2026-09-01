package com.example.integrador.Repository;

import com.example.integrador.Enum.TipoPessoa;
import com.example.integrador.Model.Usuario;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    @EntityGraph(attributePaths = "pessoa")
    Optional<Usuario> findByEmailIgnoreCase(String email);

    @EntityGraph(attributePaths = "pessoa")
    List<Usuario> findAllByOrderByEmailAsc();

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByPessoaId(Long pessoaId);

    boolean existsByTipoPessoa(TipoPessoa tipoPessoa);
}
