package com.example.integrador.Exception;

public class GeracaoRelatorioException extends RuntimeException {
    public GeracaoRelatorioException(Throwable causa) { super("Não foi possível gerar o PDF. Tente novamente.", causa); }
}
