package krilovs.andrejs.app.rate;

import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletResponse;
import krilovs.andrejs.app.util.AbstractServletTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static krilovs.andrejs.app.util.HttpTestServer.json;
import static krilovs.andrejs.app.util.JsonWriter.JSON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

class RateServletTest extends AbstractServletTest {
    @Mock
    private RateService rateService;

    @Override
    protected HttpServlet servlet() {
        return new RateServlet(rateService);
    }

    @Override
    protected String[] paths() {
        return new String[]{"/exchangeRates", "/exchangeRate/*", "/other"};
    }

    @Test
    void shouldReturnAllRates() {
        when(rateService.findAll()).thenReturn(List.of(
                response("EUR", "USD", new BigDecimal("1.1224")),
                response("EUR", "PLN", new BigDecimal("4.389"))
        ));

        var response = server.get("/exchangeRates");
        assertEquals(HttpServletResponse.SC_OK, response.statusCode());
        assertEquals(2, json(response).size());
    }

    @Test
    void shouldReturnRateByPair() {
        when(rateService.findByPair("EUR", "USD")).thenReturn(
                Optional.of(response("EUR", "USD", new BigDecimal("1.1224")))
        );

        var response = server.get("/exchangeRate/eurusd");
        assertEquals(HttpServletResponse.SC_OK, response.statusCode());
        assertEquals("EUR", json(response).get("baseCurrency").get("code").asText());
        assertEquals("USD", json(response).get("targetCurrency").get("code").asText());
        assertEquals("1.1224", json(response).get("rate").asText());
    }

    @Test
    void shouldReturnBadRequestWhenPairHasIncorrectFormat() {
        assertError(
                server.get("/exchangeRate/EUR"),
                HttpServletResponse.SC_BAD_REQUEST,
                "Incorrect currency pair, e.g. USDEUR"
        );
    }

    @Test
    void shouldNotFindRatePair() {
        when(rateService.findByPair("EUR", "TRY")).thenReturn(Optional.empty());

        var response = server.get("/exchangeRate/EURTRY");
        assertEquals(HttpServletResponse.SC_NOT_FOUND, response.statusCode());
        assertEquals("Exchange pair not found", json(response).get("message").asText());
        assertEquals(List.of("EUR", "TRY"), JSON.convertValue(json(response).get("value"), new TypeReference<List<String>>() {}));
    }

    @Test
    void shouldPersistNewRate() {
        var request = new RateRequest("BYN", "EUR", new BigDecimal("2.9"));
        when(rateService.persist(request)).thenReturn(response("BYN", "EUR", new BigDecimal("2.9")));

        var response = server.post("/exchangeRates", """
                {"baseCurrencyCode": "BYN", "targetCurrencyCode": "EUR", "rate": 2.9}""");
        assertEquals(HttpServletResponse.SC_CREATED, response.statusCode());
        assertEquals("2.9", json(response).get("rate").asText());
    }

    @Test
    void shouldNotPersistRateWhenBaseCurrencyIsMissing() {
        assertValidationError(
                server.post("/exchangeRates", "{\"targetCurrencyCode\": \"EUR\", \"rate\": 1.2}"),
                "baseCurrencyCode",
                "Base currency code should be defined"
        );
    }

    @Test
    void shouldUpdateRate() {
        var request = new RateRequest("EUR", "USD", new BigDecimal("1.2"));
        when(rateService.update(request)).thenReturn(response("EUR", "USD", new BigDecimal("1.2")));

        var response = server.patch("/exchangeRate/eurusd", "{\"rate\": 1.2}");
        assertEquals(HttpServletResponse.SC_ACCEPTED, response.statusCode());
        assertEquals("1.2", json(response).get("rate").asText());
    }

    @Test
    void shouldNotUpdateRate() {
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
