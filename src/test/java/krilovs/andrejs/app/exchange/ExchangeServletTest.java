package krilovs.andrejs.app.exchange;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletResponse;
import krilovs.andrejs.app.util.AbstractServletTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;

import java.math.BigDecimal;

import static krilovs.andrejs.app.util.HttpTestServer.json;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

class ExchangeServletTest extends AbstractServletTest {
    @Mock
    private ExchangeService exchangeService;

    @Override
    protected HttpServlet servlet() {
        return new ExchangeServlet(exchangeService);
    }

    @Override
    protected String[] paths() {
        return new String[]{"/exchange", "/other"};
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
