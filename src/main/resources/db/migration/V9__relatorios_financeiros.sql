ALTER TABLE conta_pagar ADD COLUMN competencia DATE;
ALTER TABLE conta_pagar ADD COLUMN classificacao VARCHAR(20);
ALTER TABLE conta_pagar ADD COLUMN dia_vencimento_salario INTEGER;

UPDATE conta_pagar SET competencia = date_trunc('month', data_vencimento)::date
WHERE data_vencimento IS NOT NULL;
UPDATE conta_pagar SET classificacao = 'DESPESA';
UPDATE conta_pagar c SET classificacao = 'SALARIO',
    dia_vencimento_salario = extract(day FROM c.data_vencimento)::integer
FROM pessoa p WHERE c.pessoa_id = p.id AND p.dtype = 'Motorista'
    AND c.descricao = 'Salário - ' || p.nome;
ALTER TABLE conta_pagar ADD CONSTRAINT ck_conta_pagar_classificacao CHECK (classificacao IN ('SALARIO','DESPESA'));
ALTER TABLE conta_pagar ADD CONSTRAINT ck_competencia_primeiro_dia CHECK (competencia IS NULL OR extract(day FROM competencia) = 1);
ALTER TABLE conta_pagar ADD CONSTRAINT ck_dia_salario CHECK (dia_vencimento_salario BETWEEN 1 AND 31);

-- Reconcilia somente contas com comprovantes: sem comprovante não se inventa saída.
UPDATE conta_pagar c SET status = CASE WHEN p.total >= c.valor THEN 'PAGO' ELSE 'PENDENTE' END
FROM (SELECT conta_pagar_id, sum(valor) total FROM pagamento GROUP BY conta_pagar_id) p
WHERE p.conta_pagar_id = c.id AND c.valor > 0 AND p.total >= 0;

ALTER TABLE pagamento ADD COLUMN chave_idempotencia VARCHAR(36);
ALTER TABLE pagamento ADD COLUMN requisicao_idempotencia VARCHAR(255);
CREATE UNIQUE INDEX uk_pagamento_idempotencia ON pagamento(chave_idempotencia) WHERE chave_idempotencia IS NOT NULL;
CREATE INDEX ix_pagamento_conta ON pagamento(conta_pagar_id);
CREATE INDEX ix_pagamento_data ON pagamento(data_pagamento);
CREATE INDEX ix_recebimento_data ON recebimento(data_recebimento);
CREATE INDEX ix_conta_pagar_competencia ON conta_pagar(competencia, pessoa_id);
CREATE INDEX ix_conta_receber_vencimento ON conta_receber(data_vencimento);
-- Índice não exclusivo preserva possíveis duplicidades legadas; o lock de pessoa
-- serializa a criação de novos salários e a rotina consulta a competência antes de inserir.
CREATE INDEX ix_salario_motorista_competencia ON conta_pagar(pessoa_id, competencia) WHERE classificacao = 'SALARIO';

CREATE TABLE saldo_base_caixa (
    id BIGINT PRIMARY KEY CHECK (id = 1),
    data_referencia DATE NOT NULL,
    saldo NUMERIC(19,2) NOT NULL,
    versao BIGINT NOT NULL DEFAULT 0
);
