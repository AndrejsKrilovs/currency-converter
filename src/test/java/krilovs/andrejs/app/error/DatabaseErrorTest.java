package krilovs.andrejs.app.error;

import jakarta.persistence.PersistenceException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.postgresql.util.PSQLException;
import org.postgresql.util.PSQLState;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;

import static krilovs.andrejs.app.util.TestDatabase.persistence;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseErrorTest {
    private static final String INSERT_RATE = "INSERT INTO rate (base_currency, target_currency, rate) VALUES (?1, ?2, ?3)";
    private static final String INSERT_CURRENCY = "INSERT INTO currency (code, name, symbol) VALUES (?1, ?2, ?3)";

    static Stream<Arguments> databaseFailures() {
        return Stream.of(
                Arguments.of(INSERT_RATE, List.of("QQQ", "EUR", BigDecimal.ONE),
                        "Key (base_currency)=(QQQ) is not present in table \"currency\"",
                        "Key (base_currency)=(QQQ) is not present",
                        List.of("QQQ", "EUR")),
                Arguments.of(INSERT_CURRENCY, List.of("EUR", "Euro", "€"),
                        "Key (code)=(EUR) already exists",
                        "Key (code)=(EUR) already exists",
                        List.of("EUR", "Euro")),
                Arguments.of("INSERT INTO currency (code, name, symbol) VALUES ('XDL', 'No detail', 'TOOLONG')", List.of(),
                        null, null, List.of())
        );
    }

    @ParameterizedTest
    @MethodSource("databaseFailures")
    @SuppressWarnings({"resource", "SqlSourceToSinkFlow"})
    void shouldDescribeDatabaseFailure(String sql, List<Object> parameters, String detail, String clientDetail, List<String> bind) {
        var failure = assertThrows(PersistenceException.class, () -> persistence().inTransaction(em -> {
            var query = em.createNativeQuery(sql);
            for (var i = 0; i < parameters.size(); i++) {
                query.setParameter(i + 1, parameters.get(i));
            }
            return query.executeUpdate();
        }));

        var debug = DatabaseError.toDebug(failure);
        assertEquals(detail, debug.detail());
        assertTrue(debug.sql().startsWith("INSERT INTO"));
        assertEquals(bind, debug.bind().subList(0, bind.size()));
        if (detail == null) {
            assertTrue(debug.cause().contains("value too long for type character varying(3)"));
        }
        else {
            assertNull(debug.cause());
        }

        var client = DatabaseError.toClient(failure);
        assertEquals(clientDetail, client.detail());
        assertNull(client.sql());
        assertNull(client.bind());
        assertNull(client.cause());
    }

    static Stream<Arguments> failuresWithoutDatabaseDetail() {
        return Stream.of(
                Arguments.of(new PersistenceException("plain failure"), "plain failure"),
                Arguments.of(new PersistenceException((String) null), null),
                Arguments.of(new PersistenceException("outer", new IllegalStateException("middle", new IllegalStateException("inner"))), "inner"),
                Arguments.of(new PersistenceException(new PSQLException("connection refused", PSQLState.CONNECTION_FAILURE)), "connection refused")
        );
    }

    @ParameterizedTest
    @MethodSource("failuresWithoutDatabaseDetail")
    void shouldFallBackToRootMessageWithoutDatabaseDetail(PersistenceException failure, String rootMessage) {
        var debug = DatabaseError.toDebug(failure);
        assertNull(debug.detail());
        assertNull(debug.sql());
        assertEquals(List.of(), debug.bind());
        assertEquals(rootMessage, debug.cause());

        assertNull(DatabaseError.toClient(failure).detail());
    }
}