package com.example.integrador;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.sql.DriverManager;
import java.util.UUID;

/** Repete os contratos do dashboard no PostgreSQL real, sem tocar no schema público. */
@EnabledIfEnvironmentVariable(named = "DASHBOARD_TEST_DB_URL", matches = ".+")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DashboardPostgresIntegrationTests extends DashboardIntegrationTests {
    private static final String SCHEMA = "test_dashboard_" + UUID.randomUUID().toString().replace("-", "");
    private static boolean schemaCriado;

    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry properties) throws Exception {
        try (var connection = DriverManager.getConnection(url(), usuario(), senha());
             var sql = connection.createStatement()) {
            sql.executeUpdate("CREATE SCHEMA " + SCHEMA);
            schemaCriado = true;
        }
        String jdbc = url() + (url().contains("?") ? "&" : "?") + "currentSchema=" + SCHEMA;
        properties.add("spring.datasource.url", () -> jdbc);
        properties.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        properties.add("spring.datasource.username", DashboardPostgresIntegrationTests::usuario);
        properties.add("spring.datasource.password", DashboardPostgresIntegrationTests::senha);
        properties.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        properties.add("spring.flyway.enabled", () -> "true");
        properties.add("spring.flyway.schemas", () -> SCHEMA);
        properties.add("spring.flyway.default-schema", () -> SCHEMA);
    }

    @AfterAll
    static void removerApenasSchemaDoTeste() throws Exception {
        if (!schemaCriado) return;
        // O identificador é fixado acima a partir de UUID, nunca recebido do ambiente/cliente.
        try (var connection = DriverManager.getConnection(url(), usuario(), senha());
             var sql = connection.createStatement()) {
            sql.executeUpdate("DROP SCHEMA " + SCHEMA + " CASCADE");
        }
    }

    private static String url() { return System.getenv("DASHBOARD_TEST_DB_URL"); }
    private static String usuario() { return System.getenv("DASHBOARD_TEST_DB_USER"); }
    private static String senha() { return System.getenv("DASHBOARD_TEST_DB_PASSWORD"); }
}
