package com.example.integrador;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.sql.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="RECEBER_TEST_DB_URL",matches=".+")
class ContasReceberPostgresMigrationTests {
    @Test void atualizarV9PreservaHistoricoReabreSaldoELiberaParcelas()throws Exception {
        emSchema((schema,c)->{
            flyway(schema,"9").migrate();
            executar(c,"INSERT INTO pessoa(id,dtype,nome,tipo_pessoa,status_matricula) VALUES(101,'Aluno','João','ALUNO','ATIV0')");
            executar(c,"INSERT INTO conta_receber(id,aluno_id,descricao,valor,status,data_vencimento) VALUES(201,101,'Parcial',300,'PAGO','2026-10-10'),(202,101,'Sem comprovante',100,'PAGO',NULL),(203,101,'Quitada',100,'PAGO','2026-10-12')");
            executar(c,"INSERT INTO recebimento(id,conta_receber_id,valor,data_recebimento,forma_pagamento) VALUES(301,201,100,'2026-10-16','PIX'),(302,203,100,'2026-10-16','PIX')");
            assertThat(flyway(schema,"10").migrate().migrationsExecuted).isEqualTo(1);flyway(schema,"10").validate();
            try(var s=c.createStatement();var r=s.executeQuery("SELECT status,competencia,dia_vencimento_recorrencia FROM conta_receber WHERE id=201")){
                r.next();assertThat(r.getString(1)).isEqualTo("PENDENTE");assertThat(r.getDate(2).toString()).isEqualTo("2026-10-01");assertThat(r.getInt(3)).isEqualTo(10);
            }
            try(var s=c.createStatement();var r=s.executeQuery("SELECT status FROM conta_receber WHERE id=202")){r.next();assertThat(r.getString(1)).isEqualTo("PAGO");}
            try(var s=c.createStatement();var r=s.executeQuery("SELECT status_matricula FROM pessoa WHERE id=101")){r.next();assertThat(r.getString(1)).isEqualTo("ATIV0");}
            executar(c,"INSERT INTO recebimento(id,conta_receber_id,valor,data_recebimento,forma_pagamento) VALUES(303,201,200,'2026-10-17','DINHEIRO')");
            try(var s=c.createStatement();var r=s.executeQuery("SELECT count(*),sum(valor) FROM recebimento WHERE conta_receber_id=201")){
                r.next();assertThat(r.getInt(1)).isEqualTo(2);assertThat(r.getBigDecimal(2)).isEqualByComparingTo("300");
            }
        });
    }
    @Test void bancoVazioRecebeV1AteV10()throws Exception { emSchema((schema,c)->{assertThat(flyway(schema,"10").migrate().migrationsExecuted).isEqualTo(10);flyway(schema,"10").validate();}); }
    private Flyway flyway(String schema,String target){return Flyway.configure().dataSource(url(),usuario(),senha()).schemas(schema).defaultSchema(schema).locations("classpath:db/migration").target(target).load();}
    private void emSchema(Verificacao v)throws Exception {
        String schema="test_receber_migration_"+UUID.randomUUID().toString().replace("-","");
        try(var c=DriverManager.getConnection(url(),usuario(),senha())) {
            executar(c,"CREATE SCHEMA "+schema);
            try{c.setSchema(schema);v.executar(schema,c);}
            finally{c.setSchema("public");if(!schema.matches("test_receber_migration_[a-f0-9]{32}"))throw new IllegalStateException();executar(c,"DROP SCHEMA "+schema+" CASCADE");}
        }
    }
    private void executar(Connection c,String sql)throws Exception{try(var s=c.createStatement()){s.executeUpdate(sql);}}
    private interface Verificacao{void executar(String schema,Connection c)throws Exception;}
    static String url(){return System.getenv("RECEBER_TEST_DB_URL");}
    static String usuario(){return System.getenv("RECEBER_TEST_DB_USER");}
    static String senha(){return System.getenv("RECEBER_TEST_DB_PASSWORD");}
}
