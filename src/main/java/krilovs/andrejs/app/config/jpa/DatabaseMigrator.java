package krilovs.andrejs.app.config.jpa;

import krilovs.andrejs.app.config.AppConfig;
import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.LiquibaseException;
import liquibase.resource.ClassLoaderResourceAccessor;
import lombok.RequiredArgsConstructor;

import java.sql.DriverManager;
import java.sql.SQLException;

@RequiredArgsConstructor
public class DatabaseMigrator {
    private final AppConfig config;

    public void migrate() {
        var db = config.database();
        try (var connection = DriverManager.getConnection(db.jdbcString(), db.user(), db.password())) {
            var database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try (var liquibase = new Liquibase(config.liquibase().changelog(), new ClassLoaderResourceAccessor(), database)) {
                liquibase.update();
            }
        }
        catch (SQLException | LiquibaseException e) {
            throw new IllegalStateException("Database migration failed", e);
        }
    }
}
