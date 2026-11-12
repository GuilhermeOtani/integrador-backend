-- A integração foi descontinuada. Mantém a V6 original para preservar
-- o histórico e os checksums do Flyway; remove somente seu armazenamento.
-- Os registros legados dessas quatro tabelas também são removidos.
DROP TABLE IF EXISTS whatsapp_entrega;
DROP TABLE IF EXISTS whatsapp_assinatura;
DROP TABLE IF EXISTS whatsapp_lote;
DROP TABLE IF EXISTS whatsapp_configuracao;
