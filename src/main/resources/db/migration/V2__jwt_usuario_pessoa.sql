UPDATE pessoa
SET tipo_pessoa = CASE dtype
    WHEN 'Aluno' THEN 'ALUNO'
    WHEN 'Motorista' THEN 'MOTORISTA'
    WHEN 'Pessoa' THEN 'ADMIN'
    ELSE tipo_pessoa
END
WHERE tipo_pessoa IS NULL;

ALTER TABLE pessoa DROP CONSTRAINT IF EXISTS pessoa_status_matricula_check;
ALTER TABLE pessoa ADD CONSTRAINT pessoa_status_matricula_check
    CHECK (status_matricula IN ('ATIV0', 'INATIV0', 'PENDENTE'));

ALTER TABLE usuario ADD COLUMN pessoa_id BIGINT;

ALTER TABLE usuario ALTER COLUMN email SET NOT NULL;
ALTER TABLE usuario ALTER COLUMN senha SET NOT NULL;
ALTER TABLE usuario ALTER COLUMN tipo_pessoa SET NOT NULL;
ALTER TABLE usuario ALTER COLUMN pessoa_id SET NOT NULL;

ALTER TABLE usuario ADD CONSTRAINT fk_usuario_pessoa
    FOREIGN KEY (pessoa_id) REFERENCES pessoa(id) ON DELETE CASCADE;
ALTER TABLE usuario ADD CONSTRAINT uk_usuario_pessoa UNIQUE (pessoa_id);
CREATE UNIQUE INDEX uk_usuario_email_lower ON usuario (LOWER(email));
