package krilovs.andrejs.app.config;

import io.agroal.api.AgroalDataSource;
import io.agroal.api.configuration.supplier.AgroalDataSourceConfigurationSupplier;
import io.agroal.api.security.NamePrincipal;
import io.agroal.api.security.SimplePassword;
import io.agroal.narayana.NarayanaTransactionIntegration;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceConfiguration;
import jakarta.persistence.PersistenceUnitTransactionType;
import jakarta.persistence.SharedCacheMode;
import com.arjuna.ats.jta.TransactionManager;
import jakarta.transaction.Status;
import jakarta.transaction.UserTransaction;
import krilovs.andrejs.app.config.logging.PersistenceSessionLog;
import krilovs.andrejs.app.config.logging.WideEvent;
import krilovs.andrejs.app.util.DynamicEntities;
import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.LiquibaseException;
import liquibase.resource.ClassLoaderResourceAccessor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.persistence.config.PersistenceUnitProperties;
import org.eclipse.persistence.dynamic.DynamicClassLoader;
import org.eclipse.persistence.dynamic.DynamicEntity;
import org.eclipse.persistence.jpa.PersistenceProvider;
import org.eclipse.persistence.jpa.dynamic.JPADynamicHelper;
import org.eclipse.persistence.logging.SessionLog;
import org.eclipse.persistence.transaction.JTA11TransactionController;
import org.eclipse.persistence.transaction.JTATransactionController;
import org.postgresql.xa.PGXADataSource;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Objects;
import java.util.function.Function;

@Slf4j
public class PersistenceConfig implements AutoCloseable {
    private final DynamicEntities types;
    private final AgroalDataSource dataSource;
    private final EntityManagerFactory factory;
    private final DynamicClassLoader classLoader;
    private final AppConfig config;

    public PersistenceConfig() {
        config = AppConfigLoader.load();
        classLoader = new DynamicClassLoader(Thread.currentThread().getContextClassLoader());
        types = DynamicEntities.define(classLoader);

        migrate();
        var tm = TransactionManager.transactionManager();
        var registry =
                new com.arjuna.ats.internal.jta.transaction.arjunacore.TransactionSynchronizationRegistryImple();

        try {
            dataSource = AgroalDataSource.from(new AgroalDataSourceConfigurationSupplier()
                    .connectionPoolConfiguration(pool -> pool
                            .transactionIntegration(new NarayanaTransactionIntegration(tm, registry))
                            .maxSize(config.database().maxConnectionPoolSize())
                            .connectionFactoryConfiguration(cf -> cf
                                    .connectionProviderClass(PGXADataSource.class)
                                    .jdbcUrl(config.database().jdbcString())
                                    .principal(new NamePrincipal(config.database().user()))
                                    .credential(new SimplePassword(config.database().password())))));
        }
        catch (SQLException e) {
            throw new IllegalStateException("Cannot create data source", e);
        }

        JTATransactionController.setDefaultTransactionManager(tm);
        JTA11TransactionController.setDefaultTransactionSynchronizationRegistry(registry);
        factory = createEntityManagerFactory(dataSource);
        new JPADynamicHelper(factory).addTypes(false, false, types.all());
    }

    @Override
    public void close() {
        factory.close();
        dataSource.close();
    }

    /**
     * Runs {@code work} with a fresh EntityManager and closes it afterwards, without a transaction.
     * Intended for read-only queries. Do not return lazy streams/entities that need the EntityManager.
     */
    public <T> T withEntityManager(Function<EntityManager, T> work) {
        try (var em = createEntityManager()) {
            return work.apply(em);
        }
    }

    /**
     * Runs {@code work} in a JTA transaction with a fresh EntityManager.
     * Commits on success, rolls back on any exception.
     */
    public <T> T inTransaction(Function<EntityManager, T> work) {
        var tx = com.arjuna.ats.jta.UserTransaction.userTransaction();
        try (var em = createEntityManager()) {
            tx.begin();
            try {
                em.joinTransaction();
                T result = work.apply(em);
                tx.commit();
                return result;
            }
            catch (Exception e) {
                rollback(tx);
                throw e;
            }
        }
        catch (RuntimeException e) {
            throw e;
        }
        catch (Exception e) {
            throw new IllegalStateException("Transaction failed", e);
        }
    }

    /** New instance of the dynamic entity, e.g. {@link DynamicEntities#CURRENCY}. */
    public DynamicEntity newEntity(String type) {
        return types.get(type).newDynamicEntity();
    }

    /** Generated class of the dynamic entity, for {@code find} and native queries. */
    public Class<? extends DynamicEntity> entityClass(String type) {
        return types.get(type).getJavaClass().asSubclass(DynamicEntity.class);
    }

    private EntityManager createEntityManager() {
        return factory.createEntityManager();
    }

    private static void rollback(UserTransaction tx) {
        try {
            int status = tx.getStatus();
            if (status == Status.STATUS_ACTIVE || status == Status.STATUS_MARKED_ROLLBACK) {
                tx.rollback();
            }
        }
        catch (Exception ignored) {
            log.error("Failed to rollback JTA transaction", ignored);
            WideEvent.put("tx_rollback_error", ignored.getMessage());
        }
    }

    /** Applies Liquibase changelogs over a plain JDBC connection, outside of JTA. */
    private void migrate() {
        try (var connection = DriverManager.getConnection(config.database().jdbcString(), config.database().user(), config.database().password())) {
            var database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try (var liquibase = new Liquibase(config.liquibase().changelog(), new ClassLoaderResourceAccessor(), database)) {
                liquibase.update();
            }
        }
        catch (SQLException | LiquibaseException e) {
            throw new IllegalStateException("Database migration failed", e);
        }
    }

    /** Persistence unit described in code instead of META-INF/persistence.xml. */
    private EntityManagerFactory createEntityManagerFactory(AgroalDataSource dataSource) {
        return new PersistenceConfiguration(Objects.requireNonNullElse(config.database().configName(), "playground"))
                .provider(PersistenceProvider.class.getName())
                .sharedCacheMode(SharedCacheMode.ENABLE_SELECTIVE)
                .transactionType(PersistenceUnitTransactionType.JTA)
                .property(PersistenceUnitProperties.CLASSLOADER, classLoader)
                .property(PersistenceUnitProperties.JTA_DATASOURCE, dataSource)
                .property(PersistenceUnitProperties.LOGGING_LEVEL, SessionLog.WARNING_LABEL)
                .property(PersistenceUnitProperties.LOGGING_PARAMETERS, Boolean.TRUE.toString())
                .property(PersistenceUnitProperties.DDL_GENERATION, PersistenceUnitProperties.NONE)
                .property(PersistenceUnitProperties.LOGGING_LOGGER, PersistenceSessionLog.class.getName())
                .property("%s%s".formatted(PersistenceUnitProperties.CATEGORY_LOGGING_LEVEL_, SessionLog.SQL), SessionLog.FINE_LABEL)
                .createEntityManagerFactory();
    }
}
