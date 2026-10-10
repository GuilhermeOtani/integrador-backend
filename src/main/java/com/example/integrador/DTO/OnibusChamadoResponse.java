package com.example.integrador.DTO;

import com.example.integrador.Enum.StatusOnibus;
import com.example.integrador.Model.Onibus;

public record OnibusChamadoResponse(Long id, String numeroIdentificacao, String placa,
                                   String modelo, StatusOnibus statusOnibus) {
    public static OnibusChamadoResponse fromEntity(Onibus onibus) {
        return new OnibusChamadoResponse(onibus.getId(), onibus.getNumeroIdentificacao(),
                onibus.getPlaca(), onibus.getModelo(), onibus.getStatusOnibus());
    }
}
