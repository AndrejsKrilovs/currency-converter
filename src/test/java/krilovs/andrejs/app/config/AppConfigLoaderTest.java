package krilovs.andrejs.app.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.UncheckedIOException;
import java.util.Map;
import java.util.stream.Stream;

import static krilovs.andrejs.app.util.JsonWriter.JSON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppConfigLoaderTest {
    private static final String VALID = "config/valid.yaml";
    private static final String PASSWORD = "p$ss\\w:rd #1";

    static Stream<Arguments> environments() {
        return Stream.of(
                Arguments.of(Map.of("DB_NAME", "payment", "DB_PASSWORD", PASSWORD),
                        8080, "jdbc:postgresql://localhost:5432/payment", "user"),
                Arguments.of(Map.of("PORT", "9090", "DB_HOST", "db", "DB_NAME", "payment", "DB_USER", "admin", "DB_PASSWORD", PASSWORD),
                        9090, "jdbc:postgresql://db:5432/payment", "admin")
        );
    }

    @ParameterizedTest
    @MethodSource("environments")
    void shouldSubstitutePlaceholders(Map<String, String> env, int port, String jdbcString, String user) {
        var config = AppConfigLoader.load(VALID, env::get);

        assertEquals(port, config.server().port());
        assertEquals(jdbcString, config.database().jdbcString());
        assertEquals(user, config.database().user());
        assertEquals(PASSWORD, config.database().password());
        assertEquals(5, config.database().maxConnectionPoolSize());
        assertEquals("changelog.yaml", config.liquibase().changelog());
    }

    @Test
    void shouldFailWhenVariableIsNotSetAndHasNoDefault() {
        var thrown = assertThrows(IllegalStateException.class, () -> AppConfigLoader.load(VALID, _ -> null));

        assertEquals("Environment variable DB_NAME is not set and has no default", thrown.getMessage());
    }

    @Test
    void shouldFailWhenResourceIsMissing() {
        var thrown = assertThrows(IllegalStateException.class, () -> AppConfigLoader.load("config/missing.yaml"));

        assertEquals("Config not found on classpath: config/missing.yaml", thrown.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"config/malformed.yaml", "config/mismatch.yaml"})
    void shouldFailWhenResourceCannotBeRead(String resource) {
        var thrown = assertThrows(UncheckedIOException.class, () -> AppConfigLoader.load(resource));

        assertEquals("Failed to read " + resource, thrown.getMessage());
    }

    @Test
    void shouldLoadDefaultResourceFromRealEnvironment() {
        var config = AppConfigLoader.load();

        assertEquals("test-password", config.database().password());
        assertEquals("db/changelog/db.changelog-master.yaml", config.liquibase().changelog());
    }

    @Test
    void shouldResolveNestedObjectsAndArraysAndKeepOtherValues() throws Exception {
        var tree = JSON.readTree("""
                {"list": ["${A}", "plain", 1], "nested": {"key": "${B:fallback}"}, "number": 5, "flag": true}""");

        var resolved = AppConfigLoader.resolve(tree, name -> "A".equals(name) ? "from-env" : null);

        assertEquals("from-env", resolved.get("list").get(0).asText());
        assertEquals("plain", resolved.get("list").get(1).asText());
        assertEquals(1, resolved.get("list").get(2).asInt());
        assertEquals("fallback", resolved.get("nested").get("key").asText());
        assertEquals(5, resolved.get("number").asInt());
        assertTrue(resolved.get("flag").asBoolean());
    }
}