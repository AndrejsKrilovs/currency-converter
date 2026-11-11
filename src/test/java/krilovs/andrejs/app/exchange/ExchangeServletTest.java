package krilovs.andrejs.app.exchange;

import jakarta.servlet.http.HttpServletResponse;
import krilovs.andrejs.app.util.AbstractTestClass;
import krilovs.andrejs.app.util.HttpTestServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.ParameterizedTest;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static krilovs.andrejs.app.util.HttpTestServer.json;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExchangeServletTest extends AbstractTestClass {
    @Mock
    private ExchangeService exchangeService;

    private HttpTestServer server;

    @BeforeEach
    void startServer() {
        server = new HttpTestServer(new ExchangeServlet(exchangeService), "/exchange", "/other");
    }

    @AfterEach
    void stopServer() {
        server.close();
    }

    @Test
    void shouldConvertAmount() {
        var request = new ExchangeRequest("EUR", "USD", new BigDecimal("100"));
        when(exchangeService.convert(request)).thenReturn(new ExchangeResponse(
                currency("EUR"), currency("USD"),
                new BigDecimal("1.12"), new BigDecimal("100"), new BigDecimal("112")
        ));

        var response = server.get("/exchange?from=eur&to=usd&amount=100");
        assertEquals(HttpServletResponse.SC_OK, response.statusCode());
        assertEquals("EUR", json(response).get("baseCurrency").get("code").asText());
        assertEquals("USD", json(response).get("targetCurrency").get("code").asText());
        assertEquals("1.12", json(response).get("rate").asText());
        assertEquals("100", json(response).get("amount").asText());
        assertEquals("112", json(response).get("convertedAmount").asText());
    }

    @Test
    void shouldNotConvertSameCurrencies() {
        var response = server.get("/exchange?from=eur&to=eur&amount=1");
        assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.statusCode());
        assertEquals(
                "Base currency must differ from target currency",
                json(response).get("errors").get("currenciesDifferent").get(0).asText()
        );
    }

    @Test
    void shouldNotConvertWhenIncorrectAmount() {
        var response = server.get("/exchange?from=eur&to=usd&amount=aaa");
        assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.statusCode());
        assertEquals("Amount must be a number", json(response).get("error").asText());
    }

    @ParameterizedTest
    @ValueSource(strings = {"to=eur&amount=1", "from=&to=eur&amount=1", "from=%20&to=eur&amount=1"})
    void shouldNotConvertWhenParameterIsMissingOrBlank(String query) {
        var response = server.get("/exchange?" + query);
        assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.statusCode());
        assertEquals("Missing query parameter: from", json(response).get("error").asText());
    }

    @Test
    void shouldReturnIncorrectEndpointWhenPathIsUnsupported() {
        var response = server.get("/other");
        assertEquals(HttpServletResponse.SC_NOT_FOUND, response.statusCode());
        assertEquals("Incorrect endpoint", json(response).get("error").asText());
    }
}
