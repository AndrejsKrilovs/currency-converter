package krilovs.andrejs.app.currency;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletResponse;
import krilovs.andrejs.app.util.AbstractServletTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.stream.Stream;

import static krilovs.andrejs.app.util.HttpTestServer.json;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CurrencyServletTest extends AbstractServletTest {
    @Override
    protected HttpServlet servlet() {
        return new CurrencyServlet(new CurrencyService(PERSISTENCE));
    }

    @Override
    protected String[] paths() {
        return new String[]{"/currencies", "/currency/*", "/other"};
    }

    @Test
    void shouldReturnAllCurrencies() {
        var response = server.get("/currencies");
        assertEquals(HttpServletResponse.SC_OK, response.statusCode());
        assertTrue(json(response).findValuesAsText("code").containsAll(List.of("EUR", "USD", "PLN")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"eur", "EUR"})
    void shouldReturnCurrencyByCode(String code) {
        var response = server.get("/currency/" + code);
        assertEquals(HttpServletResponse.SC_OK, response.statusCode());
        assertCurrency(json(response), "EUR", "Euro", "€");
    }

    @Test
    void shouldReturnNotFoundWhenCurrencyDoesNotExist() {
        var value = assertNotFound(server.get("/currency/xxx"), "Currency code not found");
        assertEquals("XXX", value.asText());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/currency", "/currency/", "/currency/%20"})
    void shouldReturnBadRequestWhenCurrencyCodeIsMissing(String path) {
        assertError(server.get(path), HttpServletResponse.SC_BAD_REQUEST, "Missing currency code");
    }

    @Test
    void shouldPersistNewCurrency() {
        var created = server.post("/currencies", """
                {"code": "XPC", "name": "Persisted", "symbol": "xp"}""");
        assertEquals(HttpServletResponse.SC_CREATED, created.statusCode());
        assertCurrency(json(created), "XPC", "Persisted", "xp");

        var loaded = server.get("/currency/xpc");
        assertEquals(HttpServletResponse.SC_OK, loaded.statusCode());
        assertCurrency(json(loaded), "XPC", "Persisted", "xp");
    }

    @ParameterizedTest
    @MethodSource("rejectedByDatabase")
    void shouldReturnConflictWhenDatabaseRejectsCurrency(String body, String detail) {
        var response = server.post("/currencies", body);

        assertEquals(HttpServletResponse.SC_CONFLICT, response.statusCode());
        if (detail == null) {
            assertTrue(json(response).isEmpty());
        }
        else {
            assertEquals(detail, json(response).get("detail").asText());
        }
    }

    @Test
    void shouldNotProvideIncorrectJson() {
        assertError(server.post("/currencies", ""), HttpServletResponse.SC_NOT_ACCEPTABLE, "Incorrect json provided");
    }

    @ParameterizedTest
    @CsvSource(value = {"GET,", "POST,{}"})
    void shouldReturnIncorrectEndpointWhenPathIsUnsupported(String method, String body) {
        assertIncorrectEndpoint(method, body);
    }

    static Stream<Arguments> rejectedByDatabase() {
        return Stream.of(
                // unique violation: PostgreSQL explains it in its Detail
                Arguments.of("""
                        {"code": "EUR", "name": "Euro again", "symbol": "€"}""", "Key (code)=(EUR) already exists"),
                // value too long: PostgreSQL gives no Detail, so the client gets an empty error
                Arguments.of("""
                        {"code": "XTL", "name": "Too long symbol", "symbol": "ABCD"}""", null)
        );
    }

    private static void assertCurrency(JsonNode json, String code, String name, String symbol) {
        assertEquals(code, json.get("code").asText());
        assertEquals(name, json.get("name").asText());
        assertEquals(symbol, json.get("symbol").asText());
    }
}
