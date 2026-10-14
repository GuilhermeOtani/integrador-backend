package com.example.integrador.Repository;

import com.example.integrador.Enum.TipoPessoa;
import com.example.integrador.Model.Usuario;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

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

    @Query(value = """
            select count(*) > 0 from usuario u join pessoa p on p.id = u.pessoa_id
            where u.id = :id and u.ativo = true and u.token_version = :ver
              and (p.dtype <> 'Aluno' or p.arquivado = false)
            """, nativeQuery = true)
    boolean tokenElegivel(Long id, long ver);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Usuario> findAllByTipoPessoaAndAtivoTrue(TipoPessoa tipoPessoa);
}
