package com.example.integrador;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.sql.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "ARQUIVAMENTO_TEST_DB_URL", matches = ".+")
class ArquivamentoAlunoPostgresMigrationTests {
    @Test void atualizarV7PreservaRegistrosEDefaultDeTodosSubtipos() throws Exception {
        emSchema((schema, connection) -> {
            flyway(schema, "7").migrate();
            executar(connection, "INSERT INTO pessoa(id,dtype,nome,tipo_pessoa) VALUES(101,'Pessoa','Admin','ADMIN'),(102,'Aluno','Aluno','ALUNO'),(103,'Motorista','Motorista','MOTORISTA')");
            executar(connection, "INSERT INTO usuario(id,email,senha,tipo_pessoa,pessoa_id) VALUES(201,'admin@migration.test','hash','ADMIN',101),(202,'aluno@migration.test','hash','ALUNO',102)");
            executar(connection, "INSERT INTO conta_receber(id,aluno_id,valor,data_vencimento,status) VALUES(301,102,100,'2026-10-01','PAGO')");
            executar(connection, "INSERT INTO recebimento(id,conta_receber_id,valor,data_recebimento,forma_pagamento) VALUES(401,301,100,'2026-10-01','PIX')");
            assertThat(flyway(schema, "8").migrate().migrationsExecuted).isEqualTo(1);
            flyway(schema, "8").validate();
            try (var sql = connection.createStatement(); var rows = sql.executeQuery("SELECT count(*) FROM pessoa WHERE arquivado=false")) {
                rows.next(); assertThat(rows.getInt(1)).isEqualTo(3);
            }
            executar(connection, "INSERT INTO pessoa(id,dtype,nome,tipo_pessoa) VALUES(104,'Motorista','Novo motorista','MOTORISTA'),(105,'Pessoa','Novo admin','ADMIN'),(106,'Aluno','Novo aluno','ALUNO')");
            try (var sql = connection.createStatement(); var rows = sql.executeQuery("SELECT count(*) FROM pessoa WHERE arquivado=false")) {
                rows.next(); assertThat(rows.getInt(1)).isEqualTo(6);
            }
            try (var sql = connection.createStatement(); var rows = sql.executeQuery("SELECT c.status,c.valor,r.valor,u.ativo,u.token_version FROM conta_receber c JOIN recebimento r ON r.conta_receber_id=c.id JOIN usuario u ON u.pessoa_id=c.aluno_id")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("PAGO");
                assertThat(rows.getBigDecimal(2)).isEqualByComparingTo("100");
                assertThat(rows.getBigDecimal(3)).isEqualByComparingTo("100");
                assertThat(rows.getBoolean(4)).isTrue();
                assertThat(rows.getLong(5)).isZero();
            }
            try (var query = connection.prepareStatement("SELECT is_nullable,column_default FROM information_schema.columns WHERE table_schema=? AND table_name='pessoa' AND column_name='arquivado'")) {
                query.setString(1, schema);
                try (var rows = query.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString(1)).isEqualTo("NO");
                    assertThat(rows.getString(2)).contains("false");
                }
            }
            try (var sql = connection.createStatement(); var rows = sql.executeQuery("""
                    SELECT count(*) FROM pg_constraint c JOIN pg_class t ON t.oid=c.conrelid
                    JOIN pg_namespace n ON n.oid=t.relnamespace
                    WHERE n.nspname=current_schema() AND t.relname IN ('conta_receber','recebimento')
                      AND c.contype='f' AND c.confdeltype='c'
                    """)) {
                rows.next(); assertThat(rows.getInt(1)).isZero();
            }
        });
    }
    @Test void schemaVazioRecebeV1AteV8() throws Exception {
        emSchema((schema, connection) -> {
            assertThat(flyway(schema, "8").migrate().migrationsExecuted).isEqualTo(8);
            flyway(schema, "8").validate();
        });
    }
    private Flyway flyway(String schema, String target) {
        return Flyway.configure().dataSource(url(), usuario(), senha()).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration").target(target).load();
    }
    private void emSchema(Verificacao verificacao) throws Exception {
        String schema = "test_arquivo_migration_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = DriverManager.getConnection(url(), usuario(), senha())) {
            executar(connection, "CREATE SCHEMA " + schema);
            try { connection.setSchema(schema); verificacao.executar(schema, connection); }
            finally {
                connection.setSchema("public");
                if (!schema.matches("test_arquivo_migration_[a-f0-9]{32}")) throw new IllegalStateException("Schema inválido");
                executar(connection, "DROP SCHEMA " + schema + " CASCADE");
            }
        }
    }
    private void executar(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement()) { statement.execute(sql); }
    }
    private static String url() { return System.getenv("ARQUIVAMENTO_TEST_DB_URL"); }
    private static String usuario() { return System.getenv("ARQUIVAMENTO_TEST_DB_USER"); }
    private static String senha() { return System.getenv("ARQUIVAMENTO_TEST_DB_PASSWORD"); }
    @FunctionalInterface private interface Verificacao { void executar(String schema, Connection connection) throws Exception; }
}
