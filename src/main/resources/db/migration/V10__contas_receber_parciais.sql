-- Uma cobrança passa a admitir vários recebimentos, preservando cada comprovante.
ALTER TABLE recebimento DROP CONSTRAINT uk_recebimento_conta_receber;
ALTER TABLE recebimento ADD COLUMN chave_idempotencia VARCHAR(36);
ALTER TABLE recebimento ADD COLUMN requisicao_idempotencia VARCHAR(255);
CREATE UNIQUE INDEX uk_recebimento_idempotencia ON recebimento(chave_idempotencia) WHERE chave_idempotencia IS NOT NULL;
CREATE INDEX ix_recebimento_conta ON recebimento(conta_receber_id);

ALTER TABLE conta_receber ADD COLUMN competencia DATE;
ALTER TABLE conta_receber ADD COLUMN dia_vencimento_recorrencia INTEGER;
UPDATE conta_receber SET competencia = date_trunc('month', data_vencimento)::date,
    dia_vencimento_recorrencia = extract(day FROM data_vencimento)::integer
WHERE data_vencimento IS NOT NULL;
ALTER TABLE conta_receber ADD CONSTRAINT ck_receber_competencia CHECK (competencia IS NULL OR extract(day FROM competencia) = 1);
ALTER TABLE conta_receber ADD CONSTRAINT ck_receber_dia CHECK (dia_vencimento_recorrencia BETWEEN 1 AND 31);
CREATE INDEX ix_conta_receber_competencia ON conta_receber(aluno_id, competencia);

-- Quitações sem comprovantes são mantidas. Não se modifica o cadastro/matrícula do aluno.
UPDATE conta_receber c SET status = CASE WHEN r.total >= c.valor THEN 'PAGO' ELSE 'PENDENTE' END
FROM (SELECT conta_receber_id, sum(valor) total FROM recebimento GROUP BY conta_receber_id) r
WHERE r.conta_receber_id = c.id AND c.valor > 0 AND r.total >= 0;
-- Duplicidades históricas de competência permanecem; novos lançamentos são
-- serializados pelo lock do aluno e validados antes da inserção.
