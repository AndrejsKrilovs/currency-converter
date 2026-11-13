package krilovs.andrejs.app.config.jpa;

import krilovs.andrejs.app.config.AppConfig;
import liquibase.exception.LiquibaseException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.sql.SQLException;
import java.util.stream.Stream;

import static krilovs.andrejs.app.util.TestDatabase.database;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DatabaseMigratorTest {
    static Stream<Arguments> failingMigrations() {
        var real = database();
        var unreachable = new AppConfig.Database("test", "jdbc:invalid:none", "user", "password", 1);
        return Stream.of(
                // the connection cannot be opened
                Arguments.of(config(unreachable, "db/changelog/db.changelog-master.yaml"), SQLException.class),
                // connected, but the changelog does not exist
                Arguments.of(config(real, "db/changelog/missing.yaml"), LiquibaseException.class)
        );
    }

    @ParameterizedTest
    @MethodSource("failingMigrations")
    void shouldWrapMigrationFailure(AppConfig config, Class<? extends Exception> expectedCause) {
        var thrown = assertThrows(IllegalStateException.class, () -> DatabaseMigrator.migrate(config));

        assertEquals("Database migration failed", thrown.getMessage());
        assertInstanceOf(expectedCause, thrown.getCause());
    }

    private static AppConfig config(AppConfig.Database database, String changelog) {
        return new AppConfig(new AppConfig.Server(0), database, new AppConfig.Liquibase(changelog));
    }
}
