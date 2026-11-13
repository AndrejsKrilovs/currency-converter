package krilovs.andrejs.app.config;

/** Typed view of application.yaml. */
public record AppConfig(Server server, Database database, Liquibase liquibase) {
    public record Server(int port) {}

    public record Database(
            String configName,
            String jdbcString,
            String user,
            String password,
            int maxConnectionPoolSize
    ) {}

    public record Liquibase(String changelog) {}
}
