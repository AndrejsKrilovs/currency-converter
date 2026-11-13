package krilovs.andrejs.app.util;

import krilovs.andrejs.app.config.AppConfig;
import krilovs.andrejs.app.config.jpa.PersistenceConfig;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * One PostgreSQL container and one PersistenceConfig (Liquibase seed data included) shared by every test class
 * that needs a database. The container is stopped by Testcontainers when the JVM exits.
 */
public final class TestDatabase {
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");
    private static final PersistenceConfig PERSISTENCE;

    static {
        POSTGRES.start();
        PERSISTENCE = new PersistenceConfig(new AppConfig(
                new AppConfig.Server(0),
                new AppConfig.Database("shared", POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword(), 5),
                new AppConfig.Liquibase("db/changelog/db.changelog-master.yaml")
        ));
        Runtime.getRuntime().addShutdownHook(new Thread(PERSISTENCE::close));
    }

    private TestDatabase() {
    }

    /** Connection settings of the shared container, for tests that talk to the database without PersistenceConfig. */
    public static AppConfig.Database database() {
        return new AppConfig.Database("direct", POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword(), 1);
    }

    public static PersistenceConfig persistence() {
        return PERSISTENCE;
    }
}
