package com.example.integrador.DTO;

import com.example.integrador.Enum.NivelUrgencia;
import com.example.integrador.Enum.StatusChamado;
import com.example.integrador.Enum.TipoPessoa;
import com.example.integrador.Model.ChamadoManutencao;
import java.time.LocalDate;

public record ChamadoManutencaoResponse(
        Long id, String descricao, LocalDate dataAbertura, NivelUrgencia urgencia,
        StatusChamado status, String observacaoAdmin, OnibusChamadoResponse onibus,
        SolicitanteResponse solicitante
) {
    public record SolicitanteResponse(Long pessoaId, String nome, TipoPessoa tipoPessoa) {}

    public static ChamadoManutencaoResponse fromEntity(ChamadoManutencao chamado) {
        var pessoa = chamado.getSolicitante();
        return new ChamadoManutencaoResponse(chamado.getId(), chamado.getDescricao(),
                chamado.getDataAbertura(), chamado.getUrgencia(), chamado.getStatus(),
                chamado.getObservacaoAdmin(), OnibusChamadoResponse.fromEntity(chamado.getOnibus()),
                new SolicitanteResponse(pessoa.getId(), pessoa.getNome(), pessoa.getTipoPessoa()));
    }
}
