package com.example.integrador.Whatsapp;

public final class WhatsappTipos {
    private WhatsappTipos() {}
    public enum StatusAssinatura { PENDENTE, ATIVA, CANCELADA }
    public enum Publico { TODOS, ALUNOS, MOTORISTAS }
    public enum Origem { MANUAL, AUTOMATICA }
    public enum StatusEntrega {
        PENDENTE, PROCESSANDO, ACEITA, ENVIADA, ENTREGUE, LIDA, FALHA, INCERTA, SUPRIMIDA, EXPIRADA
    }
}
