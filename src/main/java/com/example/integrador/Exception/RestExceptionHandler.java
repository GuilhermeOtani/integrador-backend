package com.example.integrador.Exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class RestExceptionHandler {

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ProblemDetail formatoInvalido(Exception exception) {
        return problema(HttpStatus.BAD_REQUEST, "Requisição inválida", "JSON, identificador ou enum inválido");
    }

    @ExceptionHandler(ConflitoException.class)
    public ProblemDetail conflito(ConflitoException exception) {
        return problema(HttpStatus.CONFLICT, "Conflito", exception.getMessage());
    }

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ProblemDetail naoEncontrado(RecursoNaoEncontradoException exception) {
        return problema(HttpStatus.NOT_FOUND, "Recurso não encontrado", exception.getMessage());
    }

    @ExceptionHandler(RegraNegocioException.class)
    public ProblemDetail regraNegocio(RegraNegocioException exception) {
        return problema(HttpStatus.BAD_REQUEST, "Requisição inválida", exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail validacao(MethodArgumentNotValidException exception) {
        ProblemDetail detail = problema(HttpStatus.BAD_REQUEST, "Dados inválidos",
                "Revise os campos informados");
        Map<String, String> erros = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                erros.putIfAbsent(error.getField(), error.getDefaultMessage()));
        detail.setProperty("errors", erros);
        return detail;
    }

    @ExceptionHandler({AuthenticationException.class, UsernameNotFoundException.class})
    public ProblemDetail credenciaisInvalidas(RuntimeException exception) {
        return problema(HttpStatus.UNAUTHORIZED, "Não autorizado", "E-mail ou senha inválidos");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail integridade(DataIntegrityViolationException exception) {
        return problema(HttpStatus.CONFLICT, "Conflito",
                "A operação viola uma restrição de unicidade ou relacionamento");
    }

    private ProblemDetail problema(HttpStatus status, String title, String detail) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(status, detail);
        problema.setTitle(title);
        return problema;
    }
}
