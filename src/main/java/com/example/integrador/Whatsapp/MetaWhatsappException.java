package com.example.integrador.Whatsapp;

/** Erro sanitizado: não transporta resposta bruta, token, telefone ou conteúdo pessoal. */
public class MetaWhatsappException extends RuntimeException {
    private final boolean incerto;
    private final boolean repetivel;
    public MetaWhatsappException(String codigo, boolean incerto, boolean repetivel) {
        super(codigo); this.incerto = incerto; this.repetivel = repetivel;
    }
    public boolean incerto() { return incerto; }
    public boolean repetivel() { return repetivel; }
}
