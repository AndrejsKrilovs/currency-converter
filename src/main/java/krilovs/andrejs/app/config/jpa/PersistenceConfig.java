package krilovs.andrejs.app.config.jpa;

import com.arjuna.ats.internal.jta.transaction.arjunacore.TransactionSynchronizationRegistryImple;
import io.agroal.api.AgroalDataSource;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import com.arjuna.ats.jta.TransactionManager;
import jakarta.transaction.Status;
import jakarta.transaction.TransactionSynchronizationRegistry;
import jakarta.transaction.UserTransaction;
import krilovs.andrejs.app.config.AppConfig;
import krilovs.andrejs.app.config.AppConfigLoader;
import krilovs.andrejs.app.config.logging.WideEvent;
import krilovs.andrejs.app.util.DynamicEntities;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.persistence.dynamic.DynamicClassLoader;
import org.eclipse.persistence.dynamic.DynamicEntity;
import org.eclipse.persistence.jpa.dynamic.JPADynamicHelper;
import org.eclipse.persistence.transaction.JTA11TransactionController;
import org.eclipse.persistence.transaction.JTATransactionController;

import java.util.function.Function;

import static com.arjuna.ats.jta.UserTransaction.userTransaction;

@Slf4j
public class PersistenceConfig implements AutoCloseable {
    /** EntityManager of the transaction running on this thread, if any. */
    private static final ThreadLocal<EntityManager> ACTIVE = new ThreadLocal<>();

    private final DynamicEntities types;
    private final AgroalDataSource dataSource;
    private final EntityManagerFactory factory;

    public PersistenceConfig() {
        this(AppConfigLoader.load());
    }

    public PersistenceConfig(AppConfig config) {
        var classLoader = new DynamicClassLoader(Thread.currentThread().getContextClassLoader());
        DatabaseMigrator.migrate(config);

        var transactionManager = TransactionManager.transactionManager();
        TransactionSynchronizationRegistry registry = new TransactionSynchronizationRegistryImple();
        JTATransactionController.setDefaultTransactionManager(transactionManager);
        JTA11TransactionController.setDefaultTransactionSynchronizationRegistry(registry);

        AgroalDataSource createdDataSource = null;
        EntityManagerFactory createdFactory = null;
        try {
            var createdTypes = DynamicEntities.define(classLoader);
            createdDataSource = DataSourceFactory.create(config.database(), transactionManager, registry);
            createdFactory = PersistenceUnitFactory.create(config, createdDataSource, classLoader);
            new JPADynamicHelper(createdFactory).addTypes(false, false, createdTypes.all());

            types = createdTypes;
            dataSource = createdDataSource;
            factory = createdFactory;
        }
        catch (RuntimeException | Error e) {
            var cleanupFailure = closeAll(createdFactory, createdDataSource);
            if (cleanupFailure != null) {
                e.addSuppressed(cleanupFailure);
            }
            throw e;
        }
    }

    @Override
    public void close() {
        var failure = closeAll(factory, dataSource);
        if (failure != null) {
            throw failure;
        }
    }

    /**
     * Runs {@code work} with a fresh EntityManager and closes it afterwards, without a transaction.
     * Intended for read-only queries. Do not return lazy streams/entities that need the EntityManager.
     */
    public <T> T withEntityManager(Function<EntityManager, T> work) {
        var current = ACTIVE.get();
        if (current != null) {
            // Called from inside inTransaction(): reuse its EntityManager (same persistence context, sees
            // uncommitted changes). It is owned and closed by the outer inTransaction(), not by us.
            return work.apply(current);
        }
        try (var em = createEntityManager()) {
            return work.apply(em);
        }
    }

    /**
     * Runs {@code work} in a JTA transaction with a fresh EntityManager.
     * Commits on success, rolls back on any exception.
     * <p>
     * Nested calls follow REQUIRED semantics: an inner call joins the outer transaction and reuses its
     * EntityManager, and commit/rollback is decided by the outermost call. If the inner work throws, the
     * transaction is marked rollback-only, so the outer commit cannot succeed even if the caller swallows
     * the exception.
     */
    public <T> T inTransaction(Function<EntityManager, T> work) {
        var currentTransaction = ACTIVE.get();
        if (currentTransaction != null) {
            return joinActiveTransaction(currentTransaction, work);
        }
        var tx = userTransaction();
        try (var em = createEntityManager()) {
            tx.begin();
            try {
                em.joinTransaction();
                ACTIVE.set(em);
                T result = work.apply(em);
                tx.commit();
                return result;
            }
            catch (RuntimeException | Error originalException) {
                rollback(tx, originalException);
                throw originalException;
            }
            catch (Exception originalException) {
                rollback(tx, originalException);
                throw new IllegalStateException("Transaction failed", originalException);
            }
            finally {
                ACTIVE.remove();
            }
        }
        catch (RuntimeException | Error e) {
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

    private static <T> T joinActiveTransaction(EntityManager em, Function<EntityManager, T> work) {
        try {
            return work.apply(em);
        }
        catch (RuntimeException | Error e) {
            try {
                userTransaction().setRollbackOnly();
            }
            catch (Exception markException) {
                e.addSuppressed(markException);
            }
            throw e;
        }
    }

    private EntityManager createEntityManager() {
        return factory.createEntityManager();
    }

    /**
     * Closes the factory, then the data source, even if the first close fails. Null resources are skipped.
     * Returns the first failure with any later ones attached as suppressed, or null if both closed cleanly.
     */
    private static RuntimeException closeAll(EntityManagerFactory factory, AgroalDataSource dataSource) {
        RuntimeException failure = null;
        if (factory != null) {
            try {
                factory.close();
            }
            catch (RuntimeException e) {
                failure = e;
            }
        }
        if (dataSource != null) {
            try {
                dataSource.close();
            }
            catch (RuntimeException e) {
                if (failure == null) {
                    failure = e;
                }
                else {
                    failure.addSuppressed(e);
                }
            }
        }
        return failure;
    }

    private static void rollback(UserTransaction tx, Throwable originalException) {
        try {
            int status = tx.getStatus();
            if (status == Status.STATUS_ACTIVE || status == Status.STATUS_MARKED_ROLLBACK) {
                tx.rollback();
            }
        }
        catch (Exception rollbackException) {
            originalException.addSuppressed(rollbackException);
            log.error("Failed to rollback JTA transaction", rollbackException);
            WideEvent.put("tx_rollback_error", rollbackException.getMessage());
        }
    }
}
