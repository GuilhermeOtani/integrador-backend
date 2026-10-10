-- Não inventar um autor/ônibus para registros antigos incompletos nem apagar histórico.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM chamado_manutencao
               WHERE descricao IS NULL OR btrim(descricao) = '' OR data_abertura IS NULL
                  OR urgencia IS NULL OR status IS NULL OR onibus_id IS NULL OR responsavel_id IS NULL) THEN
        RAISE EXCEPTION 'Existem chamados incompletos. Corrija descrição, data, prioridade, status, ônibus e solicitante antes de aplicar V5.';
    END IF;
END $$;

ALTER TABLE chamado_manutencao
    ALTER COLUMN descricao TYPE VARCHAR(2000),
    ADD COLUMN observacao_admin VARCHAR(2000),
    ALTER COLUMN descricao SET NOT NULL,
    ALTER COLUMN data_abertura SET NOT NULL,
    ALTER COLUMN urgencia SET NOT NULL,
    ALTER COLUMN status SET NOT NULL,
    ALTER COLUMN onibus_id SET NOT NULL,
    ALTER COLUMN responsavel_id SET NOT NULL,
    ADD CONSTRAINT chamado_descricao_nao_vazia CHECK (btrim(descricao) <> '');

CREATE INDEX idx_chamado_solicitante ON chamado_manutencao (responsavel_id, data_abertura DESC, id DESC);
CREATE INDEX idx_chamado_onibus ON chamado_manutencao (onibus_id);
