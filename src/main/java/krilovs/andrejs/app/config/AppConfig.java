package krilovs.andrejs.app.config;

/** Typed view of application.yaml. */
public record AppConfig(Database database, Liquibase liquibase) {
    public record Database(
            String configName,
            String jdbcString,
            String user,
            String password,
            int maxConnectionPoolSize
    ) {}

    public record Liquibase(String changelog) {}
}
