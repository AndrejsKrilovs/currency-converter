package krilovs.andrejs.app.config;

import krilovs.andrejs.app.config.jpa.PersistenceConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static krilovs.andrejs.app.util.DynamicEntities.CURRENCY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Runs the real persistence stack (Liquibase, Agroal, Narayana, EclipseLink) against a disposable PostgreSQL. */
@Testcontainers
class PersistenceConfigTest {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    static PersistenceConfig persistence;

    @BeforeAll
    static void start() {
        persistence = new PersistenceConfig(new AppConfig(
                new AppConfig.Server(0),
                new AppConfig.Database("test", POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword(), 5),
                new AppConfig.Liquibase("db/changelog/db.changelog-master.yaml")
        ));
    }

    @AfterAll
    static void stop() {
        persistence.close();
    }

    @Test
    void committedChangesAreVisibleOutsideTheTransaction() {
        persistence.inTransaction(em -> {
            em.persist(currency("XCM", "Committed"));
            return null;
        });

        assertNotNull(find("XCM"));
    }

    @Test
    void failedTransactionRollsBack() {
        assertThrows(IllegalStateException.class, () -> persistence.inTransaction(em -> {
            em.persist(currency("XRB", "Rolled back"));
            em.flush();
            throw new IllegalStateException("boom");
        }));

        assertNull(find("XRB"));
    }

    @Test
    void nestedTransactionJoinsOuterOne() {
        persistence.inTransaction(outer -> {
            outer.persist(currency("XNJ", "Nested join"));
            persistence.inTransaction(inner -> {
                assertEquals(outer, inner);
                return null;
            });
            // read inside the transaction sees the uncommitted row through the same EntityManager
            assertNotNull(persistence.withEntityManager(em -> em.find(persistence.entityClass(CURRENCY), "XNJ")));
            return null;
        });

        assertNotNull(find("XNJ"));
    }

    @Test
    void failedNestedTransactionPreventsOuterCommit() {
        assertThrows(Exception.class, () -> persistence.inTransaction(outer -> {
            outer.persist(currency("XNF", "Nested failure"));
            try {
                persistence.inTransaction(_ -> {
                    throw new IllegalStateException("inner failed");
                });
            }
            catch (IllegalStateException swallowed) {
                // the caller ignores the failure, but the transaction is already rollback-only
            }
            return null;
        }));

        assertNull(find("XNF"));
    }

    private static Object find(String code) {
        return persistence.withEntityManager(em -> em.find(persistence.entityClass(CURRENCY), code));
    }

    private static Object currency(String code, String name) {
        var currency = persistence.newEntity(CURRENCY);
        currency.set("code", code);
        currency.set("name", name);
        currency.set("symbol", code);
        return currency;
    }
}
