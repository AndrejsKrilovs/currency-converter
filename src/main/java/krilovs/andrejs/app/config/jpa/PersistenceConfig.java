package krilovs.andrejs.app.config.jpa;

import com.arjuna.ats.internal.jta.transaction.arjunacore.TransactionSynchronizationRegistryImple;
import io.agroal.api.AgroalDataSource;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import com.arjuna.ats.jta.TransactionManager;
import jakarta.transaction.Status;
import jakarta.transaction.UserTransaction;
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
    private final DynamicEntities types;
    private final AgroalDataSource dataSource;
    private final EntityManagerFactory factory;

    public PersistenceConfig() {
        var config = AppConfigLoader.load();
        var classLoader = new DynamicClassLoader(Thread.currentThread().getContextClassLoader());
        DatabaseMigrator.migrate(config);

        var transactionManager = TransactionManager.transactionManager();
        var registry = new TransactionSynchronizationRegistryImple();
        JTATransactionController.setDefaultTransactionManager(transactionManager);
        JTA11TransactionController.setDefaultTransactionSynchronizationRegistry(registry);

        types = DynamicEntities.define(classLoader);
        dataSource = DataSourceFactory.create(config.database(), transactionManager, registry);
        factory = PersistenceUnitFactory.create(config, dataSource, classLoader);
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
        var tx = userTransaction();
        try (var em = createEntityManager()) {
            tx.begin();
            try {
                em.joinTransaction();
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

    private EntityManager createEntityManager() {
        return factory.createEntityManager();
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
