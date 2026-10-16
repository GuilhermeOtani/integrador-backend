package com.example.integrador;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.sql.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="RELATORIOS_TEST_DB_URL",matches=".+")
class RelatoriosPostgresMigrationTests {
    @Test void atualizarV8ReabreSaldoPreservaComprovantesEPreencheCompetencia() throws Exception {
        emSchema((schema,c)->{
            flyway(schema,"8").migrate();
            executar(c,"INSERT INTO pessoa(id,dtype,nome,tipo_pessoa) VALUES(101,'Motorista','José','MOTORISTA')");
            executar(c,"INSERT INTO conta_pagar(id,pessoa_id,descricao,valor,status,data_vencimento) VALUES(201,101,'Salário - José',100,'PAGO','2026-10-10'),(202,101,'Despesa',100,'PAGO','2026-10-11'),(203,101,'Sem comprovante',100,'PAGO',NULL)");
            executar(c,"INSERT INTO pagamento(id,conta_pagar_id,valor,data_pagamento,forma_pagamento) VALUES(301,201,60,'2026-10-15','PIX'),(302,202,100,'2026-10-15','PIX')");
            assertThat(flyway(schema,"9").migrate().migrationsExecuted).isEqualTo(1);
            flyway(schema,"9").validate();
            try(var s=c.createStatement();var r=s.executeQuery("SELECT id,status,classificacao,competencia,dia_vencimento_salario FROM conta_pagar ORDER BY id")){
                assertThat(r.next()).isTrue();assertThat(r.getString("status")).isEqualTo("PENDENTE");assertThat(r.getString("classificacao")).isEqualTo("SALARIO");
                assertThat(r.getDate("competencia").toString()).isEqualTo("2026-10-01");assertThat(r.getInt("dia_vencimento_salario")).isEqualTo(10);
                assertThat(r.next()).isTrue();assertThat(r.getString("status")).isEqualTo("PAGO");assertThat(r.getString("classificacao")).isEqualTo("DESPESA");
                assertThat(r.next()).isTrue();assertThat(r.getString("status")).isEqualTo("PAGO");assertThat(r.getDate("competencia")).isNull();
            }
            try(var s=c.createStatement();var r=s.executeQuery("SELECT count(*),sum(valor) FROM pagamento")){
                r.next();assertThat(r.getInt(1)).isEqualTo(2);assertThat(r.getBigDecimal(2)).isEqualByComparingTo("160");
            }
        });
    }
    @Test void schemaVazioRecebeTodasMigrationsSemSaldoPresumido() throws Exception {
        emSchema((schema,c)->{
            assertThat(flyway(schema,"9").migrate().migrationsExecuted).isEqualTo(9);
            try(var s=c.createStatement();var r=s.executeQuery("SELECT count(*) FROM saldo_base_caixa")){r.next();assertThat(r.getInt(1)).isZero();}
        });
    }
    private Flyway flyway(String schema,String target){return Flyway.configure().dataSource(url(),usuario(),senha()).schemas(schema).defaultSchema(schema).locations("classpath:db/migration").target(target).load();}
    private void emSchema(Verificacao v)throws Exception{
        String schema="test_relatorios_migration_"+UUID.randomUUID().toString().replace("-","");
        try(var c=DriverManager.getConnection(url(),usuario(),senha())){
            executar(c,"CREATE SCHEMA "+schema);
            try{c.setSchema(schema);v.executar(schema,c);}
            finally{c.setSchema("public");if(!schema.matches("test_relatorios_migration_[a-f0-9]{32}"))throw new IllegalStateException();executar(c,"DROP SCHEMA "+schema+" CASCADE");}
        }
    }
    private void executar(Connection c,String sql)throws Exception{try(var s=c.createStatement()){s.executeUpdate(sql);}}
    private interface Verificacao{void executar(String schema,Connection c)throws Exception;}
    static String url(){return System.getenv("RELATORIOS_TEST_DB_URL");}
    static String usuario(){return System.getenv("RELATORIOS_TEST_DB_USER");}
    static String senha(){return System.getenv("RELATORIOS_TEST_DB_PASSWORD");}
}
