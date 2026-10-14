-- Preserva todos os subtipos, cadastros e vínculos históricos.
ALTER TABLE pessoa ADD COLUMN arquivado BOOLEAN NOT NULL DEFAULT FALSE;
