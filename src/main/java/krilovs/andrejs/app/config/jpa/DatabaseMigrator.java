package krilovs.andrejs.app.config.jpa;

import krilovs.andrejs.app.config.AppConfig;
import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.LiquibaseException;
import liquibase.resource.ClassLoaderResourceAccessor;
import lombok.experimental.UtilityClass;

import java.sql.DriverManager;
import java.sql.SQLException;

@UtilityClass
public class DatabaseMigrator {
    public static void migrate(AppConfig app) {
        var dbProps = app.database();
        try (var connection = DriverManager.getConnection(dbProps.jdbcString(), dbProps.user(), dbProps.password())) {
            var database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try (var liquibase = new Liquibase(app.liquibase().changelog(), new ClassLoaderResourceAccessor(), database)) {
                liquibase.update();
            }
        }
        catch (SQLException | LiquibaseException e) {
            throw new IllegalStateException("Database migration failed", e);
        }
    }
}
