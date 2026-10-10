package com.example.integrador;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

/** Usa apenas schemas temporários próprios; nunca migra ou limpa o schema público. */
@EnabledIfEnvironmentVariable(named = "MANUTENCAO_TEST_DB_URL", matches = ".+")
class ManutencaoPostgresMigrationTests {
    @Test
    void bancoVazioRecebeV1AteV5() throws Exception {
        emSchemaTemporario((schema, connection) -> {
            Flyway flyway = flyway(schema, "5");
            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(5);
            flyway.validate();
            verificarEstrutura(connection, schema);
        });
    }

    @Test
    void atualizacaoV4PreservaPessoasAdminEChamados() throws Exception {
        emSchemaTemporario((schema, connection) -> {
            flyway(schema, "4").migrate();
            try (var sql = connection.createStatement()) {
                sql.executeUpdate("INSERT INTO pessoa(id,dtype,nome,tipo_pessoa) VALUES (101,'Pessoa','Admin preservado','ADMIN'),(102,'Aluno','Autor preservado','ALUNO')");
                sql.executeUpdate("INSERT INTO usuario(id,email,senha,tipo_pessoa,pessoa_id) VALUES (201,'admin@migration.test','hash-teste','ADMIN',101)");
                sql.executeUpdate("INSERT INTO onibus(id,placa,status_onibus) VALUES (301,'TESTE','OPERANDO')");
                sql.executeUpdate("INSERT INTO chamado_manutencao(id,descricao,data_abertura,urgencia,status,onibus_id,responsavel_id) VALUES (401,'Relato preservado',CURRENT_DATE,'MEDIA','ABERTO',301,102)");
            }
            assertThat(flyway(schema, "5").migrate().migrationsExecuted).isEqualTo(1);
            flyway(schema, "5").validate();
            verificarEstrutura(connection, schema);
            try (var sql = connection.createStatement(); var rows = sql.executeQuery(
                    "SELECT c.descricao,p.nome,u.ativo,u.token_version FROM chamado_manutencao c JOIN pessoa p ON p.id=c.responsavel_id CROSS JOIN usuario u WHERE c.id=401 AND u.id=201")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("Relato preservado");
                assertThat(rows.getString(2)).isEqualTo("Autor preservado");
                assertThat(rows.getBoolean(3)).isTrue();
                assertThat(rows.getLong(4)).isZero();
            }
            assertThatThrownBy(() -> executar(connection, "DELETE FROM pessoa WHERE id=102"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> executar(connection, "DELETE FROM onibus WHERE id=301"))
                    .isInstanceOf(SQLException.class);
            executar(connection, "UPDATE chamado_manutencao SET descricao='" + "x".repeat(2000) + "', observacao_admin='Observação' WHERE id=401");
        });
    }

    @Test
    void chamadoLegadoIncompletoFalhaSemApagarRegistros() throws Exception {
        emSchemaTemporario((schema, connection) -> {
            flyway(schema, "4").migrate();
            executar(connection, "INSERT INTO chamado_manutencao(id,descricao) VALUES (401,'Sem autor informado')");
            assertThatThrownBy(() -> flyway(schema, "5").migrate()).hasStackTraceContaining("Existem chamados incompletos");
            try (var sql = connection.createStatement(); var rows = sql.executeQuery("SELECT descricao FROM chamado_manutencao WHERE id=401")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("Sem autor informado");
            }
        });
    }

    private void verificarEstrutura(Connection connection, String schema) throws Exception {
        try (var query = connection.prepareStatement("SELECT column_name,is_nullable,character_maximum_length FROM information_schema.columns WHERE table_schema=? AND table_name='chamado_manutencao'")) {
            query.setString(1, schema);
            try (var rows = query.executeQuery()) {
                int obrigatorios = 0;
                while (rows.next()) {
                    String campo = rows.getString(1);
                    if (!campo.equals("observacao_admin")) {
                        assertThat(rows.getString(2)).isEqualTo("NO");
                        obrigatorios++;
                    }
                    if (campo.equals("descricao") || campo.equals("observacao_admin")) {
                        assertThat(rows.getInt(3)).isEqualTo(2000);
                    }
                }
                assertThat(obrigatorios).isEqualTo(7);
            }
        }
    }

    private Flyway flyway(String schema, String target) {
        return Flyway.configure().dataSource(url(), usuario(), senha()).schemas(schema)
                .defaultSchema(schema).locations("classpath:db/migration").target(target).load();
    }

    private void emSchemaTemporario(Verificacao verificacao) throws Exception {
        String schema = "test_manutencao_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = DriverManager.getConnection(url(), usuario(), senha())) {
            executar(connection, "CREATE SCHEMA " + schema);
            try {
                connection.setSchema(schema);
                verificacao.executar(schema, connection);
            } finally {
                connection.setSchema("public");
                // Identificador é gerado acima, sem entrada externa, e só este schema pode ser removido.
                if (!schema.matches("test_manutencao_[a-f0-9]{32}")) throw new IllegalStateException("Schema inválido");
                executar(connection, "DROP SCHEMA " + schema + " CASCADE");
            }
        }
    }

    private void executar(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement()) { statement.execute(sql); }
    }

    private String url() { return System.getenv("MANUTENCAO_TEST_DB_URL"); }
    private String usuario() { return System.getenv().getOrDefault("MANUTENCAO_TEST_DB_USER", "postgres"); }
    private String senha() { return System.getenv().getOrDefault("MANUTENCAO_TEST_DB_PASSWORD", ""); }
    @FunctionalInterface
    private interface Verificacao { void executar(String schema, Connection connection) throws Exception; }
}
