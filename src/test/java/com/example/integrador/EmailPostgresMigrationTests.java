package com.example.integrador;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.sql.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "EMAIL_TEST_DB_URL", matches = ".+")
class EmailPostgresMigrationTests {
    @Test
    void bancoVazioRecebeSeteMigracoesComAutomaticoDesativado() throws Exception {
        emSchemaTemporario((schema, connection) -> {
            assertThat(flyway(schema, "7").migrate().migrationsExecuted).isEqualTo(7);
            flyway(schema, "7").validate();
            try (var sql = connection.createStatement(); var rows = sql.executeQuery("select automatico from email_configuracao where id=1")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getBoolean(1)).isFalse();
            }
        });
    }
    @Test
    void migracaoV5PreservaCadastroEExclusaoDeAcessoNaoApagaHistorico() throws Exception {
        emSchemaTemporario((schema, connection) -> {
            flyway(schema, "5").migrate();
            executar(connection, "INSERT INTO pessoa(id,dtype,nome,tipo_pessoa) VALUES(101,'Pessoa','Admin preservado','ADMIN'),(102,'Aluno','Aluno preservado','ALUNO')");
            executar(connection, "INSERT INTO usuario(id,email,senha,tipo_pessoa,pessoa_id) VALUES(201,'admin@migration.test','hash','ADMIN',101),(202,'aluno@migration.test','hash','ALUNO',102)");
            executar(connection, "INSERT INTO viagem(id,data) VALUES(301,'2000-01-01')");
            assertThat(flyway(schema, "7").migrate().migrationsExecuted).isEqualTo(2);
            UUID lote = UUID.randomUUID();
            executar(connection, "INSERT INTO email_lote(id,data_referencia,origem,criado_em,assunto,texto,html,solicitante_id) VALUES('" + lote + "',CURRENT_DATE,'MANUAL',CURRENT_TIMESTAMP,'Programação','Conteúdo preservado','<p>Conteúdo preservado</p>',201)");
            executar(connection, "INSERT INTO email_entrega(id,lote_id,usuario_id,email,data_referencia,estado,proxima_tentativa) VALUES('" + UUID.randomUUID() + "','" + lote + "',202,'aluno@migration.test',CURRENT_DATE,'PENDENTE',CURRENT_TIMESTAMP)");
            assertThatThrownBy(() -> executar(connection, "INSERT INTO email_entrega(id,lote_id,usuario_id,email,data_referencia,estado,proxima_tentativa) VALUES('" + UUID.randomUUID() + "','" + lote + "',202,'aluno@migration.test',CURRENT_DATE,'PENDENTE',CURRENT_TIMESTAMP)"))
                    .isInstanceOf(SQLException.class);
            executar(connection, "DELETE FROM usuario WHERE id=202");
            try (var sql = connection.createStatement(); var rows = sql.executeQuery(
                    "SELECT p.nome,e.email,l.texto,v.data FROM pessoa p CROSS JOIN email_entrega e JOIN email_lote l ON l.id=e.lote_id CROSS JOIN viagem v WHERE p.id=102 AND v.id=301")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("Aluno preservado");
                assertThat(rows.getString(2)).isEqualTo("aluno@migration.test");
                assertThat(rows.getString(3)).isEqualTo("Conteúdo preservado");
                assertThat(rows.getDate(4).toLocalDate()).isEqualTo(java.time.LocalDate.of(2000, 1, 1));
            }
        });
    }
    private Flyway flyway(String schema, String target) {
        return Flyway.configure().dataSource(url(), usuario(), senha()).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration").target(target).load();
    }
    @Test
    void atualizacaoV6PreservaAssinaturasLegadasSemReativarWhatsApp() throws Exception {
        emSchemaTemporario((schema, connection) -> {
            flyway(schema, "6").migrate();
            executar(connection, "INSERT INTO pessoa(id,dtype,nome,tipo_pessoa) VALUES(101,'Aluno','Aluno preservado','ALUNO')");
            executar(connection, "INSERT INTO usuario(id,email,senha,tipo_pessoa,pessoa_id) VALUES(201,'aluno@migration.test','hash','ALUNO',101)");
            executar(connection, "INSERT INTO whatsapp_assinatura(usuario_id,numero,status,consentimento_em,janela_ativacoes,ativacoes_na_janela,revisao) VALUES(201,'+5511999999999','ATIVA',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,0)");
            assertThat(flyway(schema, "7").migrate().migrationsExecuted).isEqualTo(1);
            flyway(schema, "7").validate();
            try (var sql = connection.createStatement(); var rows = sql.executeQuery(
                    "SELECT p.nome,u.email,w.status,e.automatico FROM pessoa p JOIN usuario u ON u.pessoa_id=p.id JOIN whatsapp_assinatura w ON w.usuario_id=u.id CROSS JOIN email_configuracao e WHERE u.id=201")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("Aluno preservado");
                assertThat(rows.getString(2)).isEqualTo("aluno@migration.test");
                assertThat(rows.getString(3)).isEqualTo("ATIVA");
                assertThat(rows.getBoolean(4)).isFalse();
            }
        });
    }
    private void emSchemaTemporario(Verificacao verificacao) throws Exception {
        String schema = "test_email_migration_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = DriverManager.getConnection(url(), usuario(), senha())) {
            executar(connection, "CREATE SCHEMA " + schema);
            try {
                connection.setSchema(schema);
                verificacao.executar(schema, connection);
            } finally {
                connection.setSchema("public");
                if (!schema.matches("test_email_migration_[a-f0-9]{32}")) throw new IllegalStateException("Schema inválido");
                executar(connection, "DROP SCHEMA " + schema + " CASCADE");
            }
        }
    }
    private void executar(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement()) { statement.execute(sql); }
    }
    private static String url() { return System.getenv("EMAIL_TEST_DB_URL"); }
    private static String usuario() { return System.getenv("EMAIL_TEST_DB_USER"); }
    private static String senha() { return System.getenv("EMAIL_TEST_DB_PASSWORD"); }
    @FunctionalInterface private interface Verificacao { void executar(String schema, Connection connection) throws Exception; }
}
