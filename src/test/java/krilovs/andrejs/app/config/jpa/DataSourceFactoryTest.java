package krilovs.andrejs.app.config.jpa;

import io.agroal.api.AgroalDataSource;
import jakarta.transaction.TransactionManager;
import jakarta.transaction.TransactionSynchronizationRegistry;
import krilovs.andrejs.app.config.AppConfig;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class DataSourceFactoryTest {
    @Test
    void shouldWrapSqlExceptionOfDataSourceCreation() {
        var cause = new SQLException("pool failed");
        var database = new AppConfig.Database(
                "test",
                "jdbc:postgresql://localhost/none",
                "user",
                "password",
                1
        );

        try (var _ = mockStatic(AgroalDataSource.class, _ -> {
            throw cause;
        })) {
            var thrown = assertThrows(IllegalStateException.class, () -> {
                try (var ds = DataSourceFactory.create(
                        database, mock(TransactionManager.class), mock(TransactionSynchronizationRegistry.class))) {
                    assertNotNull(ds);
                }
            });

            assertEquals("Cannot create data source", thrown.getMessage());
            assertSame(cause, thrown.getCause());
        }
    }
}