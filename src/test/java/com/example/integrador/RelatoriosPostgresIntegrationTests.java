package com.example.integrador;

import com.example.integrador.Enum.*;
import com.example.integrador.Model.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import java.sql.DriverManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

/** Executa os mesmos contratos e uma disputa real por saldo em schema exclusivo. */
@EnabledIfEnvironmentVariable(named="RELATORIOS_TEST_DB_URL", matches=".+")
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class RelatoriosPostgresIntegrationTests extends RelatoriosIntegrationTests {
    private static final String SCHEMA = "test_relatorios_" + UUID.randomUUID().toString().replace("-", "");
    private static boolean criado;
    @Autowired PlatformTransactionManager manager;
    @DynamicPropertySource static void postgres(DynamicPropertyRegistry p) throws Exception {
        try (var c=DriverManager.getConnection(url(),usuario(),senha()); var s=c.createStatement()) {
            s.executeUpdate("CREATE SCHEMA " + SCHEMA); criado=true;
        }
        p.add("spring.datasource.url",()->url()+(url().contains("?")?"&":"?")+"currentSchema="+SCHEMA);
        p.add("spring.datasource.username",RelatoriosPostgresIntegrationTests::usuario);
        p.add("spring.datasource.password",RelatoriosPostgresIntegrationTests::senha);
        p.add("spring.datasource.driver-class-name",()->"org.postgresql.Driver");
        p.add("spring.jpa.hibernate.ddl-auto",()->"validate");
        p.add("spring.flyway.enabled",()->"true");
        p.add("spring.flyway.schemas",()->SCHEMA); p.add("spring.flyway.default-schema",()->SCHEMA);
    }
    @AfterAll static void limparSchema() throws Exception {
        if (!criado) return;
        if (!SCHEMA.matches("test_relatorios_[a-f0-9]{32}")) throw new IllegalStateException("Schema inválido");
        try (var c=DriverManager.getConnection(url(),usuario(),senha()); var s=c.createStatement()) { s.executeUpdate("DROP SCHEMA " + SCHEMA + " CASCADE"); }
    }
    @Test void pagamentosConcorrentesNaoUltrapassamSaldo() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(manager); tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        Long[] ids=tx.execute(s->{
            var m=new Motorista(); m.setNome("Concorrência isolada"); m.setTipoPessoa(TipoPessoa.MOTORISTA); em.persist(m);
            var c=new ContaPagar(); c.setMotorista(m); c.setValor(new BigDecimal("100")); c.setStatus(StatusFinanceiro.PENDENTE);
            c.setCompetencia(LocalDate.of(2026,10,1)); c.setClassificacao(ClassificacaoConta.DESPESA); em.persist(c);
            return new Long[]{m.getId(),c.getId()};
        });
        ExecutorService executor=Executors.newFixedThreadPool(2); CountDownLatch iniciar=new CountDownLatch(1);
        try {
            Callable<Object> pagar=()->{
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("admin","",List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
                try { iniciar.await(10,TimeUnit.SECONDS); return pagamentos.realizarPagamento(ids[1],FormaPagamento.PIX,new BigDecimal("70")); }
                catch (com.example.integrador.Exception.RegraNegocioException e) { return e; }
                finally { SecurityContextHolder.clearContext(); }
            };
            var a=executor.submit(pagar); var b=executor.submit(pagar); iniciar.countDown();
            List<Object> resultados=List.of(a.get(30,TimeUnit.SECONDS),b.get(30,TimeUnit.SECONDS));
            assertThat(resultados.stream().filter(Pagamento.class::isInstance).count()).isEqualTo(1);
            BigDecimal totalPago = tx.execute(s->pagamentosRepository.totalPago(ids[1]));
            assertThat(totalPago).isEqualByComparingTo("70");
        } finally {
            executor.shutdownNow();
            tx.executeWithoutResult(s->{
                em.createQuery("delete Pagamento p where p.contaPagar.id=:id").setParameter("id",ids[1]).executeUpdate();
                em.createQuery("delete ContaPagar c where c.id=:id").setParameter("id",ids[1]).executeUpdate();
                em.createQuery("delete Pessoa p where p.id=:id").setParameter("id",ids[0]).executeUpdate();
            });
        }
    }
    static String url(){return System.getenv("RELATORIOS_TEST_DB_URL");}
    static String usuario(){return System.getenv("RELATORIOS_TEST_DB_USER");}
    static String senha(){return System.getenv("RELATORIOS_TEST_DB_PASSWORD");}
}
