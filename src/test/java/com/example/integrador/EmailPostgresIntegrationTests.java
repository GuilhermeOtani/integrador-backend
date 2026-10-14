package com.example.integrador;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.sql.DriverManager;
import java.util.UUID;

/** Repete os testes com schema temporário próprio, sem alterar as tabelas públicas. */
@EnabledIfEnvironmentVariable(named = "EMAIL_TEST_DB_URL", matches = ".+")
class EmailPostgresIntegrationTests extends EmailIntegrationTests {
    private static final String SCHEMA = "test_email_" + UUID.randomUUID().toString().replace("-", "");
    private static boolean criado;
    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry properties) throws Exception {
        try (var connection = DriverManager.getConnection(url(), usuario(), senha()); var sql = connection.createStatement()) {
            sql.executeUpdate("CREATE SCHEMA " + SCHEMA);
            criado = true;
        }
        String jdbc = url() + (url().contains("?") ? "&" : "?") + "currentSchema=" + SCHEMA;
        properties.add("spring.datasource.url", () -> jdbc);
        properties.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        properties.add("spring.datasource.username", EmailPostgresIntegrationTests::usuario);
        properties.add("spring.datasource.password", EmailPostgresIntegrationTests::senha);
        properties.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        properties.add("spring.flyway.enabled", () -> "true");
        properties.add("spring.flyway.schemas", () -> SCHEMA);
        properties.add("spring.flyway.default-schema", () -> SCHEMA);
    }
    @AfterAll
    static void limparSchemaDoTeste() throws Exception {
        if (!criado) return;
        if (!SCHEMA.matches("test_email_[a-f0-9]{32}")) throw new IllegalStateException("Schema inválido");
        try (var connection = DriverManager.getConnection(url(), usuario(), senha()); var sql = connection.createStatement()) {
            sql.executeUpdate("DROP SCHEMA " + SCHEMA + " CASCADE");
        }
    }
    private static String url() { return System.getenv("EMAIL_TEST_DB_URL"); }
    private static String usuario() { return System.getenv("EMAIL_TEST_DB_USER"); }
    private static String senha() { return System.getenv("EMAIL_TEST_DB_PASSWORD"); }
}
