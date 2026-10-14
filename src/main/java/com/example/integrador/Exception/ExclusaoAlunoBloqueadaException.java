package com.example.integrador.Exception;

import com.example.integrador.DTO.SituacaoExclusaoAlunoResponse.Vinculos;

public class ExclusaoAlunoBloqueadaException extends RuntimeException {
    private final String codigo;
    private final Vinculos vinculos;
    public ExclusaoAlunoBloqueadaException(String codigo, String mensagem, Vinculos vinculos) {
        super(mensagem);
        this.codigo = codigo;
        this.vinculos = vinculos;
    }
    public String getCodigo() { return codigo; }
    public Vinculos getVinculos() { return vinculos; }
}
