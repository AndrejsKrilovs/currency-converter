package krilovs.andrejs.app.exchange;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletResponse;
import krilovs.andrejs.app.rate.RateService;
import krilovs.andrejs.app.util.AbstractServletTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static krilovs.andrejs.app.util.HttpTestServer.json;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Seeded rate used here: EUR to USD is 1.1224. */
class ExchangeServletTest extends AbstractServletTest {
    @Override
    protected HttpServlet servlet() {
        return new ExchangeServlet(new ExchangeService(new RateService(PERSISTENCE)));
    }

    @Override
    protected String[] paths() {
        return new String[]{"/exchange", "/other"};
    }

    @Test
    void shouldConvertAmount() {
        var response = server.get("/exchange?from=eur&to=usd&amount=100");

        assertEquals(HttpServletResponse.SC_OK, response.statusCode());
        assertEquals("EUR", json(response).get("baseCurrency").get("code").asText());
        assertEquals("USD", json(response).get("targetCurrency").get("code").asText());
        assertEquals("1.1224", json(response).get("rate").asText());
        assertEquals("100.0", json(response).get("amount").asText());
        assertEquals("112.24", json(response).get("convertedAmount").asText());
    }

    @Test
    void shouldNotConvertUnknownPair() {
        var value = assertNotFound(server.get("/exchange?from=eur&to=xyz&amount=1"), "Exchange pair not found");

        assertEquals("EUR", value.get(0).asText());
        assertEquals("XYZ", value.get(1).asText());
    }

    @Test
    void shouldNotConvertSameCurrencies() {
        assertValidationError(
                server.get("/exchange?from=eur&to=eur&amount=1"),
                "currenciesDifferent",
                "Base currency must differ from target currency"
        );
    }

    @Test
    void shouldNotConvertWhenIncorrectAmount() {
        assertError(
                server.get("/exchange?from=eur&to=usd&amount=aaa"),
                HttpServletResponse.SC_BAD_REQUEST,
                "Amount must be a number"
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"to=eur&amount=1", "from=&to=eur&amount=1", "from=%20&to=eur&amount=1"})
    void shouldNotConvertWhenParameterIsMissingOrBlank(String query) {
        assertError(
                server.get("/exchange?" + query),
                HttpServletResponse.SC_BAD_REQUEST,
                "Missing query parameter: from"
        );
    }

    @Test
    void shouldReturnIncorrectEndpointWhenPathIsUnsupported() {
        assertIncorrectEndpoint("GET", null);
    }
}
