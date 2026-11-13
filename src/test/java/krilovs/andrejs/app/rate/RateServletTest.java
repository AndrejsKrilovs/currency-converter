package krilovs.andrejs.app.rate;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletResponse;
import krilovs.andrejs.app.util.AbstractServletTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static krilovs.andrejs.app.util.HttpTestServer.json;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Seeded rates: EUR to every other currency (EUR to USD is 1.1224). Tests that write use pairs of their own:
 * USD/SEK (persist), USD/NOK (duplicate), USD/DKK (database rejects), USD/ZAR (update).
 */
class RateServletTest extends AbstractServletTest {
    private static final int SEEDED_RATES = 29;

    @Override
    protected HttpServlet servlet() {
        return new RateServlet(new RateService(PERSISTENCE));
    }

    @Override
    protected String[] paths() {
        return new String[]{"/exchangeRates", "/exchangeRate/*", "/other"};
    }

    @Test
    void shouldReturnAllRates() {
        var response = server.get("/exchangeRates");
        assertEquals(HttpServletResponse.SC_OK, response.statusCode());
        assertTrue(json(response).size() >= SEEDED_RATES);
    }

    @ParameterizedTest
    @ValueSource(strings = {"eurusd", "EURUSD"})
    void shouldReturnRateByPair(String pair) {
        var response = server.get("/exchangeRate/" + pair);
        assertEquals(HttpServletResponse.SC_OK, response.statusCode());
        assertEquals("EUR", json(response).get("baseCurrency").get("code").asText());
        assertEquals("USD", json(response).get("targetCurrency").get("code").asText());
        assertDecimal("1.1224", json(response).get("rate"));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "/exchangeRate/EUR | Incorrect currency pair, e.g. USDEUR",
            "/exchangeRate/EURUSDX | Incorrect currency pair, e.g. USDEUR",
            "/exchangeRate/EUR123 | Incorrect currency pair, e.g. USDEUR",
            "/exchangeRate | Missing currency pair",
            "/exchangeRate/ | Missing currency pair"
    })
    void shouldReturnBadRequestWhenPairIsMissingOrIncorrect(String path, String message) {
        assertError(server.get(path), HttpServletResponse.SC_BAD_REQUEST, message);
    }

    @Test
    void shouldNotFindRatePair() {
        var value = assertNotFound(server.get("/exchangeRate/EURXYZ"), "Exchange pair not found");
        assertEquals("EUR", value.get(0).asText());
        assertEquals("XYZ", value.get(1).asText());
    }

    @Test
    void shouldPersistNewRate() {
        var created = server.post("/exchangeRates", """
                {"baseCurrencyCode": "USD", "targetCurrencyCode": "SEK", "rate": 9.5}""");
        assertEquals(HttpServletResponse.SC_CREATED, created.statusCode());
        assertDecimal("9.5", json(created).get("rate"));

        var loaded = server.get("/exchangeRate/usdsek");
        assertEquals(HttpServletResponse.SC_OK, loaded.statusCode());
        assertDecimal("9.5", json(loaded).get("rate"));
    }

    @Test
    void shouldNotPersistDuplicateRate() {
        var body = """
                {"baseCurrencyCode": "USD", "targetCurrencyCode": "NOK", "rate": 10.4}""";
        assertEquals(HttpServletResponse.SC_CREATED, server.post("/exchangeRates", body).statusCode());

        var value = assertNotFound(server.post("/exchangeRates", body), "Rate already exists");
        assertEquals("USD", value.get("baseCurrency").asText());
        assertEquals("NOK", value.get("targetCurrency").asText());
    }

    @Test
    void shouldNotPersistRateOfUnknownCurrency() {
        var value = assertNotFound(
                server.post("/exchangeRates", "{\"baseCurrencyCode\": \"XXX\", \"targetCurrencyCode\": \"EUR\", \"rate\": 1}"),
                "Currency not found for rate pair"
        );

        assertEquals("XXX", value.get("baseCurrencyCode").asText());
    }

    @Test
    void shouldReturnConflictWhenDatabaseRejectsRate() {
        var response = server.post("/exchangeRates", """
                {"baseCurrencyCode": "USD", "targetCurrencyCode": "DKK", "rate": 10000000000000}""");

        assertEquals(HttpServletResponse.SC_CONFLICT, response.statusCode());
        assertTrue(json(response).get("detail").asText().contains("precision 19, scale 6"));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "{\"targetCurrencyCode\": \"EUR\", \"rate\": 1.2} | baseCurrencyCode | Base currency code should be defined",
            "{\"baseCurrencyCode\": \"USD\", \"targetCurrencyCode\": \"USD\", \"rate\": 1} | currenciesDifferent | Base currency must differ from target currency",
            "{\"baseCurrencyCode\": \"USD\", \"targetCurrencyCode\": \"EUR\", \"rate\": -1} | rate | Rate must be a positive number"
    })
    void shouldNotPersistInvalidRate(String body, String field, String message) {
        assertValidationError(server.post("/exchangeRates", body), field, message);
    }

    @Test
    void shouldUpdateRate() {
        server.post("/exchangeRates", """
                {"baseCurrencyCode": "USD", "targetCurrencyCode": "ZAR", "rate": 18}""");

        var updated = server.patch("/exchangeRate/usdzar", "{\"rate\": 19.5}");
        assertEquals(HttpServletResponse.SC_ACCEPTED, updated.statusCode());
        assertDecimal("19.5", json(updated).get("rate"));
        assertDecimal("19.5", json(server.get("/exchangeRate/usdzar")).get("rate"));
    }

    @Test
    void shouldNotUpdateMissingRate() {
        var value = assertNotFound(server.patch("/exchangeRate/nokdkk", "{\"rate\": 1.2}"), "Exchange rate not found");
        assertEquals("NOK", value.get("baseCurrency").asText());
        assertEquals("DKK", value.get("targetCurrency").asText());
    }

    @Test
    void shouldNotUpdateRateWhenCurrenciesAreTheSame() {
        assertValidationError(
                server.patch("/exchangeRate/usdusd", "{\"rate\": 1}"),
                "currenciesDifferent",
                "Base currency must differ from target currency"
        );
    }

    @ParameterizedTest
    @CsvSource(value = {"GET,", "POST,{}", "PATCH,{}"})
    void shouldReturnIncorrectEndpointWhenPathIsUnsupported(String method, String body) {
        assertIncorrectEndpoint(method, body);
    }
}
