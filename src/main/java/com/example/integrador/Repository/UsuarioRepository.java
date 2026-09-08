package com.example.integrador.Repository;

import com.example.integrador.Enum.TipoPessoa;
import com.example.integrador.Model.Usuario;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    @EntityGraph(attributePaths = "pessoa")
    Optional<Usuario> findByEmailIgnoreCase(String email);

    @EntityGraph(attributePaths = "pessoa")
    List<Usuario> findAllByOrderByEmailAsc();

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    boolean existsByPessoaId(Long pessoaId);

    boolean existsByTipoPessoa(TipoPessoa tipoPessoa);

    boolean existsByIdAndAtivoTrueAndTokenVersion(Long id, long tokenVersion);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Usuario> findAllByTipoPessoaAndAtivoTrue(TipoPessoa tipoPessoa);
}
