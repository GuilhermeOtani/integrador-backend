-- V6 é preservada somente por compatibilidade com bancos que já a aplicaram.
-- Não altera pessoas, usuários, viagens ou cobranças existentes.
CREATE TABLE email_configuracao (
    id BIGINT PRIMARY KEY CHECK (id = 1),
    automatico BOOLEAN NOT NULL DEFAULT FALSE,
    ultima_reserva TIMESTAMP WITH TIME ZONE
);
INSERT INTO email_configuracao (id, automatico) VALUES (1, FALSE);

CREATE TABLE email_lote (
    id UUID PRIMARY KEY,
    data_referencia DATE NOT NULL,
    dia_automatico DATE UNIQUE,
    origem VARCHAR(20) NOT NULL CHECK (origem IN ('MANUAL', 'AUTOMATICA')),
    reenvio BOOLEAN NOT NULL DEFAULT FALSE,
    motivo VARCHAR(500),
    criado_em TIMESTAMP WITH TIME ZONE NOT NULL,
    solicitante_id BIGINT,
    ignorados INTEGER NOT NULL DEFAULT 0 CHECK (ignorados >= 0),
    assunto VARCHAR(255) NOT NULL,
    texto TEXT NOT NULL,
    html TEXT NOT NULL,
    CONSTRAINT email_reenvio_motivo CHECK (NOT reenvio OR (motivo IS NOT NULL AND btrim(motivo) <> '')),
    CONSTRAINT email_automatico_data CHECK (
        (origem = 'AUTOMATICA' AND dia_automatico IS NOT NULL AND dia_automatico = data_referencia)
        OR (origem = 'MANUAL' AND dia_automatico IS NULL))
);

CREATE TABLE email_entrega (
    id UUID PRIMARY KEY,
    lote_id UUID NOT NULL REFERENCES email_lote(id),
    usuario_id BIGINT NOT NULL,
    email VARCHAR(255) NOT NULL,
    data_referencia DATE NOT NULL,
    revisao INTEGER NOT NULL DEFAULT 0 CHECK (revisao >= 0),
    estado VARCHAR(20) NOT NULL CHECK (estado IN ('PENDENTE', 'PROCESSANDO', 'ACEITA_SMTP',
        'FALHA', 'INCERTA', 'SUPRIMIDA', 'EXPIRADA')),
    tentativas INTEGER NOT NULL DEFAULT 0 CHECK (tentativas BETWEEN 0 AND 4),
    proxima_tentativa TIMESTAMP WITH TIME ZONE NOT NULL,
    reservado_em TIMESTAMP WITH TIME ZONE,
    reserva_id UUID,
    aceito_em TIMESTAMP WITH TIME ZONE,
    erro VARCHAR(100),
    CONSTRAINT uq_email_dia_destinatario_revisao UNIQUE (data_referencia, email, revisao),
    CONSTRAINT email_normalizado CHECK (email = lower(btrim(email)))
);
-- IDs históricos sem FK de usuário: exclusão de acesso preserva o histórico.
CREATE INDEX idx_email_entrega_fila ON email_entrega(estado, proxima_tentativa, id);
CREATE INDEX idx_email_entrega_lote ON email_entrega(lote_id);
CREATE INDEX idx_email_lote_criado ON email_lote(criado_em DESC, id);
