package com.example.integrador.DTO;

import com.fasterxml.jackson.annotation.JsonCreator;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.JsonNode;

public record ArquivamentoAlunoRequest(@NotNull Boolean arquivado) {
    /** Validação local: não altera a coerção JSON dos contratos legados. */
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static ArquivamentoAlunoRequest criar(JsonNode corpo) {
        if (corpo == null || !corpo.isObject() || corpo.size() > 1
                || (corpo.size() == 1 && !corpo.has("arquivado"))) {
            throw new IllegalArgumentException("Informe somente o campo arquivado.");
        }
        JsonNode valor = corpo.get("arquivado");
        if (valor == null || valor.isNull()) return new ArquivamentoAlunoRequest(null);
        if (!valor.isBoolean()) throw new IllegalArgumentException("arquivado deve ser booleano.");
        return new ArquivamentoAlunoRequest(valor.booleanValue());
    }
}
