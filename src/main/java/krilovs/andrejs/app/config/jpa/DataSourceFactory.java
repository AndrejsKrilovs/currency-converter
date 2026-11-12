package krilovs.andrejs.app.config.jpa;

import com.arjuna.ats.internal.jta.transaction.arjunacore.TransactionSynchronizationRegistryImple;

import io.agroal.api.AgroalDataSource;
import io.agroal.api.configuration.supplier.AgroalDataSourceConfigurationSupplier;
import io.agroal.api.security.NamePrincipal;
import io.agroal.api.security.SimplePassword;
import io.agroal.narayana.NarayanaTransactionIntegration;
import jakarta.transaction.TransactionManager;
import krilovs.andrejs.app.config.AppConfig;
import lombok.experimental.UtilityClass;
import org.postgresql.xa.PGXADataSource;

import java.sql.SQLException;

@UtilityClass
public class DataSourceFactory {
    static AgroalDataSource create(AppConfig.Database db,
                                   TransactionManager tm,
                                   TransactionSynchronizationRegistryImple registry) {
        try {
            return AgroalDataSource.from(new AgroalDataSourceConfigurationSupplier()
                    .connectionPoolConfiguration(pool -> pool
                            .transactionIntegration(new NarayanaTransactionIntegration(tm, registry))
                            .maxSize(db.maxConnectionPoolSize())
                            .connectionFactoryConfiguration(cf -> cf
                                    .connectionProviderClass(PGXADataSource.class)
                                    .jdbcUrl(db.jdbcString())
                                    .principal(new NamePrincipal(db.user()))
                                    .credential(new SimplePassword(db.password())))));
                    }
        catch (SQLException e) {
            throw new IllegalStateException("Cannot create data source", e);
        }
    }
}
