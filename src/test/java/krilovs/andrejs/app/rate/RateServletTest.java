package krilovs.andrejs.app.rate;

import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.servlet.http.HttpServletResponse;
import krilovs.andrejs.app.util.AbstractTestClass;
import krilovs.andrejs.app.util.HttpTestServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.ParameterizedTest;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static krilovs.andrejs.app.util.HttpTestServer.json;
import static krilovs.andrejs.app.util.JsonWriter.JSON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateServletTest extends AbstractTestClass {
    @Mock
    private RateService rateService;

    private HttpTestServer server;

    @BeforeEach
    void startServer() {
        server = new HttpTestServer(new RateServlet(rateService), "/exchangeRates", "/exchangeRate/*", "/other");
    }

    @AfterEach
    void stopServer() {
        server.close();
    }

    @Test
    void shouldReturnAllRates() {
        when(rateService.findAll()).thenReturn(List.of(
                rate("EUR", "USD", new BigDecimal("1.1224")),
                rate("EUR", "PLN", new BigDecimal("4.389"))
        ));

        var response = server.get("/exchangeRates");
        assertEquals(HttpServletResponse.SC_OK, response.statusCode());
        assertEquals(2, json(response).size());
    }

    @Test
    void shouldReturnRateByPair() {
        when(rateService.findByPair("EUR", "USD")).thenReturn(
                Optional.of(rate("EUR", "USD", new BigDecimal("1.1224")))
        );

        var response = server.get("/exchangeRate/eurusd");
        assertEquals(HttpServletResponse.SC_OK, response.statusCode());
        assertEquals("EUR", json(response).get("baseCurrency").get("code").asText());
        assertEquals("USD", json(response).get("targetCurrency").get("code").asText());
        assertEquals("1.1224", json(response).get("rate").asText());
    }

    @Test
    void shouldReturnBadRequestWhenPairHasIncorrectFormat() {
        var response = server.get("/exchangeRate/EUR");
        assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.statusCode());
        assertEquals("Incorrect currency pair, e.g. USDEUR", json(response).get("error").asText());
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
        when(rateService.persist(request)).thenReturn(rate("BYN", "EUR", new BigDecimal("2.9")));

        var response = server.post("/exchangeRates", """
                {"baseCurrencyCode": "BYN", "targetCurrencyCode": "EUR", "rate": 2.9}
        """);
        assertEquals(HttpServletResponse.SC_CREATED, response.statusCode());
        assertEquals("2.9", json(response).get("rate").asText());
    }

    @Test
    void shouldNotPersistRateWhenBaseCurrencyIsMissing() {
        var response = server.post("/exchangeRates", "{\"targetCurrencyCode\": \"EUR\", \"rate\": 1.2}");
        assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.statusCode());
        assertEquals(
                "Base currency code should be defined",
                json(response).get("errors").get("baseCurrencyCode").get(0).asText()
        );
    }

    @Test
    void shouldUpdateRate() {
        var request = new RateRequest("EUR", "USD", new BigDecimal("1.2"));
        when(rateService.update(request)).thenReturn(rate("EUR", "USD", new BigDecimal("1.2")));

        var response = server.patch("/exchangeRate/eurusd", "{\"rate\": 1.2}");
        assertEquals(HttpServletResponse.SC_ACCEPTED, response.statusCode());
        assertEquals("1.2", json(response).get("rate").asText());
    }

    @Test
    void shouldNotUpdateRate() {
        var response = server.patch("/exchangeRate/usdusd", "{\"rate\": 1}");
        assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.statusCode());
        assertEquals(
                "Base currency must differ from target currency",
                json(response).get("errors").get("currenciesDifferent").get(0).asText()
        );
    }

    @ParameterizedTest
    @CsvSource(value = {"GET,", "POST,{}", "PATCH,{}"})
    void shouldReturnIncorrectEndpointWhenPathIsUnsupported(String method, String body) {
        var response = server.send(method, "/other", body);
        assertEquals(HttpServletResponse.SC_NOT_FOUND, response.statusCode());
        assertEquals("Incorrect endpoint", json(response).get("error").asText());
    }
}
