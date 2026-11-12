package krilovs.andrejs.app.config.jpa;

import io.agroal.api.AgroalDataSource;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceConfiguration;
import jakarta.persistence.PersistenceUnitTransactionType;
import jakarta.persistence.SharedCacheMode;
import krilovs.andrejs.app.config.AppConfig;
import krilovs.andrejs.app.config.logging.PersistenceSessionLog;
import lombok.experimental.UtilityClass;
import org.eclipse.persistence.config.PersistenceUnitProperties;
import org.eclipse.persistence.dynamic.DynamicClassLoader;
import org.eclipse.persistence.jpa.PersistenceProvider;
import org.eclipse.persistence.logging.SessionLog;

import java.util.Objects;

/** Persistence unit described in code instead of META-INF/persistence.xml. */
@UtilityClass
public class PersistenceUnitFactory {
    private static final String DEFAULT_UNIT_NAME = "playground";

    static EntityManagerFactory create(AppConfig config, AgroalDataSource dataSource, DynamicClassLoader classLoader) {
        return new PersistenceConfiguration(Objects.requireNonNullElse(config.database().configName(), DEFAULT_UNIT_NAME))
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
