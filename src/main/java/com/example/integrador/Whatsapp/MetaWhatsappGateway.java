package com.example.integrador.Whatsapp;

public interface MetaWhatsappGateway {
    void verificarTemplateENumero();
    String uploadPdf(byte[] pdf, String nomeArquivo);
    String enviarProgramacao(String numero, String nome, String data, String referencia, String mediaId, String correlacao);
}
