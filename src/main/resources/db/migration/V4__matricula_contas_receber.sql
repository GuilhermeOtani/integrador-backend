ALTER TABLE conta_receber ALTER COLUMN aluno_id SET NOT NULL;
ALTER TABLE recebimento ALTER COLUMN conta_receber_id SET NOT NULL;

ALTER TABLE recebimento ADD CONSTRAINT uk_recebimento_conta_receber
    UNIQUE (conta_receber_id);

CREATE UNIQUE INDEX uk_conta_receber_aluno_vencimento
    ON conta_receber (aluno_id, data_vencimento);
