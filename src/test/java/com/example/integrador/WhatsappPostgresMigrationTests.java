package com.example.integrador;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.sql.DriverManager;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "WHATSAPP_TEST_DB_URL", matches = ".+")
class WhatsappPostgresMigrationTests {
    @Test
    void v6PreservaUsuariosEDadosAntigosEPermiteExcluirAcessoSemApagarHistorico() throws Exception {
        String schema = "test_whatsapp_migration_" + UUID.randomUUID().toString().replace("-", "");
        String url = System.getenv("WHATSAPP_TEST_DB_URL");
        String user = System.getenv("WHATSAPP_TEST_DB_USER");
        String password = System.getenv("WHATSAPP_TEST_DB_PASSWORD");
        try (var connection = DriverManager.getConnection(url, user, password); var sql = connection.createStatement()) {
            sql.executeUpdate("CREATE SCHEMA " + schema);
            try {
                Flyway.configure().dataSource(url, user, password).schemas(schema).defaultSchema(schema).target("5").load().migrate();
                connection.setSchema(schema);
                sql.executeUpdate("INSERT INTO pessoa(id,dtype,nome,tipo_pessoa) VALUES (101,'Pessoa','Admin preservado','ADMIN'),(102,'Aluno','Aluno preservado','ALUNO')");
                sql.executeUpdate("INSERT INTO usuario(id,email,senha,tipo_pessoa,pessoa_id) VALUES (201,'admin@migration.test','hash-teste','ADMIN',101),(202,'aluno@migration.test','hash-teste','ALUNO',102)");
                sql.executeUpdate("INSERT INTO viagem(id,data) VALUES (301,'1980-01-01')");
                var flyway = Flyway.configure().dataSource(url, user, password).schemas(schema).defaultSchema(schema).target("6").load();
                assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
                flyway.validate();
                try (var rows = sql.executeQuery("SELECT COUNT(*) FROM usuario")) { rows.next(); assertThat(rows.getLong(1)).isEqualTo(2); }
                try (var rows = sql.executeQuery("SELECT data FROM viagem WHERE id=301")) { rows.next(); assertThat(rows.getDate(1).toLocalDate().toString()).isEqualTo("1980-01-01"); }
                try (var rows = sql.executeQuery("SELECT automatico FROM whatsapp_configuracao WHERE id=1")) { rows.next(); assertThat(rows.getBoolean(1)).isFalse(); }
                String lote = UUID.randomUUID().toString(), entrega = UUID.randomUUID().toString();
                sql.executeUpdate("INSERT INTO whatsapp_assinatura(usuario_id,numero,status,consentimento_em,janela_ativacoes,ativacoes_na_janela,revisao) VALUES (202,'+5511999990001','ATIVA',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,1,1)");
                sql.executeUpdate("INSERT INTO whatsapp_lote(id,data_referencia,origem,publico,reenvio,criado_em,solicitante_id) VALUES ('" + lote + "',CURRENT_DATE,'MANUAL','ALUNOS',FALSE,CURRENT_TIMESTAMP,201)");
                sql.executeUpdate("INSERT INTO whatsapp_entrega(id,lote_id,usuario_id,destinatario_original_id,nome_destinatario,numero,data_referencia,revisao,assinatura_revisao,referencia,pdf,status,criado_em) VALUES ('" + entrega + "','" + lote + "',202,202,'Aluno','+5511999990001',CURRENT_DATE,0,1,'Faculdade',decode('25504446','hex'),'PENDENTE',CURRENT_TIMESTAMP)");
                sql.executeUpdate("DELETE FROM usuario WHERE id=202");
                try (var rows = sql.executeQuery("SELECT usuario_id,destinatario_original_id,octet_length(pdf) FROM whatsapp_entrega")) {
                    rows.next(); assertThat(rows.getObject(1)).isNull(); assertThat(rows.getLong(2)).isEqualTo(202); assertThat(rows.getInt(3)).isEqualTo(4);
                }
                try (var rows = sql.executeQuery("SELECT COUNT(*) FROM whatsapp_assinatura")) { rows.next(); assertThat(rows.getLong(1)).isZero(); }
                try (var rows = sql.executeQuery("SELECT COUNT(*) FROM pessoa")) { rows.next(); assertThat(rows.getLong(1)).isEqualTo(2); }
            } finally {
                connection.setSchema("public");
                // Identificador exclusivo gerado neste teste; nenhuma tabela pública é removida.
                sql.executeUpdate("DROP SCHEMA " + schema + " CASCADE");
            }
        }
    }
}
