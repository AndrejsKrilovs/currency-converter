package krilovs.andrejs.app.config;

import io.agroal.api.AgroalDataSource;
import jakarta.persistence.EntityManagerFactory;
import jakarta.transaction.NotSupportedException;
import jakarta.transaction.RollbackException;
import jakarta.transaction.SystemException;
import jakarta.transaction.UserTransaction;
import krilovs.andrejs.app.config.jpa.DataSourceFactory;
import krilovs.andrejs.app.config.jpa.DatabaseMigrator;
import krilovs.andrejs.app.config.jpa.PersistenceConfig;
import krilovs.andrejs.app.config.jpa.PersistenceUnitFactory;
import org.eclipse.persistence.dynamic.DynamicEntity;
import org.eclipse.persistence.jpa.dynamic.JPADynamicHelper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockSettings;
import org.mockito.stubbing.Answer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static com.arjuna.ats.jta.UserTransaction.userTransaction;
import static krilovs.andrejs.app.util.DynamicEntities.CURRENCY;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.withSettings;

@Testcontainers
class PersistenceConfigTest {
    private static final AppConfig MOCKED_CONFIG = new AppConfig(
            new AppConfig.Server(0),
            new AppConfig.Database("mocked", "jdbc:unused", "user", "password", 1),
            new AppConfig.Liquibase("unused")
    );

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    static PersistenceConfig persistence;

    enum Stage { DATA_SOURCE, FACTORY, ADD_TYPES }

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

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void failedWorkRollsBack(boolean markedRollbackOnly) {
        var code = markedRollbackOnly ? "XRM" : "XRB";
        var failure = new IllegalStateException("boom");

        var thrown = assertThrows(IllegalStateException.class, () -> persistence.inTransaction(em -> {
            em.persist(currency(code, "Rolled back"));
            em.flush();
            if (markedRollbackOnly) {
                markRollbackOnly();
            }
            throw failure;
        }));

        assertSame(failure, thrown);
        assertNull(find(code));
    }

    @Test
    void nestedTransactionJoinsOuterOne() {
        persistence.inTransaction(outer -> {
            outer.persist(currency("XNJ", "Nested join"));
            persistence.inTransaction(inner -> {
                assertSame(outer, inner);
                return null;
            });
            assertNotNull(persistence.withEntityManager(em -> em.find(persistence.entityClass(CURRENCY), "XNJ")));
            return null;
        });

        assertNotNull(find("XNJ"));
    }

    @Test
    void failedNestedTransactionPreventsOuterCommit() {
        var thrown = assertThrows(IllegalStateException.class, () -> persistence.inTransaction(outer -> {
            outer.persist(currency("XNF", "Nested failure"));
            try {
                persistence.inTransaction(_ -> {
                    throw new IllegalStateException("inner failed");
                });
            }
            catch (IllegalStateException _) {
            }
            return null;
        }));

        assertInstanceOf(RollbackException.class, thrown.getCause());
        assertNull(find("XNF"));
    }

    @Test
    void nestedFailureIsReportedEvenIfTransactionCannotBeMarkedRollbackOnly() {
        var inner = new AtomicReference<IllegalStateException>();

        assertThrows(IllegalStateException.class, () -> persistence.inTransaction(outer -> {
            outer.persist(currency("XNM", "Not markable"));
            try {
                persistence.inTransaction(_ -> {
                    rollbackCurrent();
                    throw new IllegalStateException("inner failed");
                });
            }
            catch (IllegalStateException swallowed) {
                inner.set(swallowed);
            }
            return null;
        }));

        assertEquals(1, inner.get().getSuppressed().length);
        assertNull(find("XNM"));
    }

    @Test
    void rollbackFailureIsSuppressedUnderTheOriginalException() throws Exception {
        var real = userTransaction();
        var tx = mock(UserTransaction.class, delegatesTo(real));
        var rollbackFailure = new SystemException("rollback failed");
        doThrow(rollbackFailure).when(tx).rollback();
        var work = new IllegalStateException("work failed");

        try (var transactions = mockStatic(com.arjuna.ats.jta.UserTransaction.class)) {
            transactions.when(com.arjuna.ats.jta.UserTransaction::userTransaction).thenReturn(tx);

            var thrown = assertThrows(IllegalStateException.class, () -> persistence.inTransaction(_ -> {
                throw work;
            }));

            assertSame(work, thrown);
            assertSame(rollbackFailure, thrown.getSuppressed()[0]);
        }
        finally {
            real.rollback();
        }
    }

    @Test
    void beginFailureIsWrappedWhenThreadAlreadyHasForeignTransaction() throws Exception {
        var foreign = userTransaction();
        foreign.begin();
        try {
            var thrown = assertThrows(IllegalStateException.class, () -> persistence.inTransaction(_ -> null));

            assertEquals("Transaction failed", thrown.getMessage());
            assertInstanceOf(NotSupportedException.class, thrown.getCause());
        }
        finally {
            foreign.rollback();
        }
    }

    @ParameterizedTest
    @CsvSource({"false,false", "true,false", "false,true", "true,true"})
    void closeReleasesBothResourcesAndReportsFailures(boolean factoryFails, boolean dataSourceFails) {
        var factoryFailure = new IllegalStateException("factory close failed");
        var dataSourceFailure = new IllegalStateException("pool close failed");
        var factory = factory(factoryFails ? factoryFailure : null);
        var dataSource = dataSource(dataSourceFails ? dataSourceFailure : null);
        var config = construct(returning(dataSource), returning(factory), withSettings(),
                () -> new PersistenceConfig(MOCKED_CONFIG));

        if (factoryFails || dataSourceFails) {
            var thrown = assertThrows(IllegalStateException.class, config::close);

            assertSame(factoryFails ? factoryFailure : dataSourceFailure, thrown);
            assertEquals(factoryFails && dataSourceFails ? 1 : 0, thrown.getSuppressed().length);
        }
        else {
            assertDoesNotThrow(config::close);
        }
        verify(factory).close();
        verify(dataSource).close();
    }

    @ParameterizedTest
    @CsvSource({
            "DATA_SOURCE,false,false",
            "FACTORY,false,false", "FACTORY,false,true",
            "ADD_TYPES,false,false", "ADD_TYPES,true,false", "ADD_TYPES,false,true", "ADD_TYPES,true,true"
    })
    void failedStartupClosesWhatWasAlreadyCreated(Stage failingStage, boolean factoryFails, boolean dataSourceFails) {
        var startupFailure = new IllegalStateException("startup failed");
        var factoryFailure = new IllegalStateException("factory close failed");
        var dataSourceFailure = new IllegalStateException("pool close failed");
        var factory = factory(factoryFails ? factoryFailure : null);
        var dataSource = dataSource(dataSourceFails ? dataSourceFailure : null);

        var thrown = assertThrows(IllegalStateException.class, () -> construct(
                failingStage == Stage.DATA_SOURCE ? failing(startupFailure) : returning(dataSource),
                failingStage == Stage.FACTORY ? failing(startupFailure) : returning(factory),
                failingStage == Stage.ADD_TYPES ? withSettings().defaultAnswer(failing(startupFailure)) : withSettings(),
                () -> {
                    try (var config = new PersistenceConfig(MOCKED_CONFIG)) {
                        return config;
                    }
                }));

        assertSame(startupFailure, thrown);
        verify(dataSource, times(failingStage == Stage.DATA_SOURCE ? 0 : 1)).close();
        verify(factory, times(failingStage == Stage.ADD_TYPES ? 1 : 0)).close();
        if (factoryFails || dataSourceFails) {
            assertEquals(1, thrown.getSuppressed().length);
            assertSame(factoryFails ? factoryFailure : dataSourceFailure, thrown.getSuppressed()[0]);
        }
        else {
            assertEquals(0, thrown.getSuppressed().length);
        }
    }

    private static PersistenceConfig construct(Answer<?> dataSourceStep, Answer<?> factoryStep,
                                               MockSettings helperSettings, Supplier<PersistenceConfig> create) {
        try (var _ = mockStatic(DatabaseMigrator.class);
             var _ = mockStatic(DataSourceFactory.class, dataSourceStep);
             var _ = mockStatic(PersistenceUnitFactory.class, factoryStep);
             var _ = mockConstruction(JPADynamicHelper.class, helperSettings)) {
            return create.get();
        }
    }

    private static Answer<Object> returning(Object value) {
        return _ -> value;
    }

    private static Answer<Object> failing(RuntimeException failure) {
        return _ -> {
            throw failure;
        };
    }

    private static EntityManagerFactory factory(RuntimeException closeFailure) {
        var factory = mock(EntityManagerFactory.class);
        if (closeFailure != null) {
            doThrow(closeFailure).when(factory).close();
        }
        return factory;
    }

    private static AgroalDataSource dataSource(RuntimeException closeFailure) {
        var dataSource = mock(AgroalDataSource.class);
        if (closeFailure != null) {
            doThrow(closeFailure).when(dataSource).close();
        }
        return dataSource;
    }

    private static void markRollbackOnly() {
        try {
            userTransaction().setRollbackOnly();
        }
        catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static void rollbackCurrent() {
        try {
            userTransaction().rollback();
        }
        catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static Object find(String code) {
        return persistence.withEntityManager(em -> em.find(persistence.entityClass(CURRENCY), code));
    }

    private static DynamicEntity currency(String code, String name) {
        var currency = persistence.newEntity(CURRENCY);
        currency.set("code", code);
        currency.set("name", name);
        currency.set("symbol", code);
        return currency;
    }
}