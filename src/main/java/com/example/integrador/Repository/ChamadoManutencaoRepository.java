package com.example.integrador.Repository;

import com.example.integrador.Model.ChamadoManutencao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.EntityGraph;
import java.util.List;

public interface ChamadoManutencaoRepository extends JpaRepository<ChamadoManutencao, Long>,
        JpaSpecificationExecutor<ChamadoManutencao> {
    @EntityGraph(attributePaths = {"onibus", "solicitante"})
    List<ChamadoManutencao> findAllBySolicitanteIdOrderByDataAberturaDescIdDesc(Long pessoaId);

    boolean existsBySolicitanteId(Long pessoaId);
    boolean existsByOnibusId(Long onibusId);
}
