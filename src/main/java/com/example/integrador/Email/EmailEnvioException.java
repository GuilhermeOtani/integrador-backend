package com.example.integrador.Email;

/** Código seguro; nunca devolver mensagens SMTP com credenciais ou dados do destinatário. */
public class EmailEnvioException extends RuntimeException {
    private final boolean incerto;
    private final boolean repetivel;
    public EmailEnvioException(String codigo, boolean incerto, boolean repetivel) {
        super(codigo);
        this.incerto = incerto;
        this.repetivel = repetivel;
    }
    public boolean isIncerto() { return incerto; }
    public boolean isRepetivel() { return repetivel; }
}
