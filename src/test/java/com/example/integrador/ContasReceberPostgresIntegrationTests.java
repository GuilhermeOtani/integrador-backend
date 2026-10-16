package com.example.integrador;

import com.example.integrador.DTO.RecebimentoResponse;
import com.example.integrador.Enum.*;
import com.example.integrador.Model.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="RECEBER_TEST_DB_URL",matches=".+")
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class ContasReceberPostgresIntegrationTests extends ContasReceberIntegrationTests {
    private static final String SCHEMA="test_receber_"+UUID.randomUUID().toString().replace("-","");
    private static boolean criado;
    @Autowired PlatformTransactionManager manager;
    @DynamicPropertySource static void postgres(DynamicPropertyRegistry p)throws Exception {
        try(var c=DriverManager.getConnection(url(),usuario(),senha());var s=c.createStatement()) { s.executeUpdate("CREATE SCHEMA "+SCHEMA); criado=true; }
        p.add("spring.datasource.url",()->url()+(url().contains("?")?"&":"?")+"currentSchema="+SCHEMA);
        p.add("spring.datasource.username",ContasReceberPostgresIntegrationTests::usuario);
        p.add("spring.datasource.password",ContasReceberPostgresIntegrationTests::senha);
        p.add("spring.datasource.driver-class-name",()->"org.postgresql.Driver");
        p.add("spring.jpa.hibernate.ddl-auto",()->"validate"); p.add("spring.flyway.enabled",()->"true");
        p.add("spring.flyway.schemas",()->SCHEMA); p.add("spring.flyway.default-schema",()->SCHEMA);
    }
    @AfterAll static void limpar()throws Exception {
        if(!criado)return;
        if(!SCHEMA.matches("test_receber_[a-f0-9]{32}"))throw new IllegalStateException();
        try(var c=DriverManager.getConnection(url(),usuario(),senha());var s=c.createStatement()) { s.executeUpdate("DROP SCHEMA "+SCHEMA+" CASCADE"); }
    }
    @Test void parcelasConcorrentesNaoUltrapassamSaldo()throws Exception { verificarConcorrencia(false); }
    @Test void mesmaChaveConcorrenteCriaSomenteUmRecebimento()throws Exception { verificarConcorrencia(true); }
    private void verificarConcorrencia(boolean mesmaChave)throws Exception {
        TransactionTemplate tx=new TransactionTemplate(manager);tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        Long[] ids=tx.execute(s->{
            var a=novoAluno("Disputa de saldo");var c=new ContaReceber();c.setAluno(a);c.setValor(new BigDecimal("300"));
            c.setDataVencimento(LocalDate.of(2026,10,10));c.setCompetencia(LocalDate.of(2026,10,1));c.setStatus(StatusFinanceiro.PENDENTE);em.persist(c);
            return new Long[]{a.getId(),c.getId()};
        });
        ExecutorService executor=Executors.newFixedThreadPool(2); CountDownLatch iniciar=new CountDownLatch(1);
        String chave=mesmaChave?UUID.randomUUID().toString():null;
        try {
            Callable<Object> pagar=()->{
                papel("ALUNO");
                try { iniciar.await(10,TimeUnit.SECONDS); return recebimentos.realizarPagamento(ids[1],FormaPagamento.PIX,ids[0],new BigDecimal("200"),chave); }
                catch(com.example.integrador.Exception.RegraNegocioException e){return e;}
                finally{org.springframework.security.core.context.SecurityContextHolder.clearContext();}
            };
            var a=executor.submit(pagar);var b=executor.submit(pagar);iniciar.countDown();
            List<Object> resultados=List.of(a.get(30,TimeUnit.SECONDS),b.get(30,TimeUnit.SECONDS));
            assertThat(resultados.stream().filter(RecebimentoResponse.class::isInstance).count()).isEqualTo(mesmaChave?2:1);
            BigDecimal total=tx.execute(s->repository.totalRecebido(ids[1])); assertThat(total).isEqualByComparingTo("200");
            Long quantidade=tx.execute(s->em.createQuery("select count(r) from Recebimento r where r.contaReceber.id=:id",Long.class).setParameter("id",ids[1]).getSingleResult());
            assertThat(quantidade).isEqualTo(1);
        } finally {
            executor.shutdownNow();
            tx.executeWithoutResult(s->{
                em.createQuery("delete Recebimento r where r.contaReceber.id=:id").setParameter("id",ids[1]).executeUpdate();
                em.createQuery("delete ContaReceber c where c.id=:id").setParameter("id",ids[1]).executeUpdate();
                em.createQuery("delete Pessoa p where p.id=:id").setParameter("id",ids[0]).executeUpdate();
            });
        }
    }
    static String url(){return System.getenv("RECEBER_TEST_DB_URL");}
    static String usuario(){return System.getenv("RECEBER_TEST_DB_USER");}
    static String senha(){return System.getenv("RECEBER_TEST_DB_PASSWORD");}
}
