package krilovs.andrejs.app.currency;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.PersistenceException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletResponse;
import krilovs.andrejs.app.util.AbstractServletTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;

import java.util.List;
import java.util.Optional;

import static krilovs.andrejs.app.util.HttpTestServer.json;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CurrencyServletTest extends AbstractServletTest {
    private static final String PLN = """
            {"code": "PLN", "name": "Polish Zloty", "symbol": "zl"}""";

    @Mock
    private CurrencyService currencyService;

    @Override
    protected HttpServlet servlet() {
        return new CurrencyServlet(currencyService);
    }

    @Override
    protected String[] paths() {
        return new String[]{"/currencies", "/currency/*", "/other"};
    }

    @Test
    void shouldReturnAllCurrencies() {
        when(currencyService.findAll()).thenReturn(List.of(
                currency("EUR", "Euro", "€"),
                currency("USD", "US Dollar", "$")
        ));

        var response = server.get("/currencies");
        assertEquals(HttpServletResponse.SC_OK, response.statusCode());
        assertEquals(2, json(response).size());
    }

    @Test
    void shouldReturnCurrencyByCode() {
        when(currencyService.findById(anyString())).thenReturn(Optional.of(currency("EUR", "Euro", "€")));

        var response = server.get("/currency/eur");
        assertEquals(HttpServletResponse.SC_OK, response.statusCode());
        assertCurrency(json(response), "EUR", "Euro", "€");
    }

    @Test
    void shouldReturnNotFoundWhenCurrencyDoesNotExist() {
        when(currencyService.findById(anyString())).thenReturn(Optional.empty());

        var response = server.get("/currency/xxx");
        assertEquals(HttpServletResponse.SC_NOT_FOUND, response.statusCode());
        assertEquals("Currency code not found", json(response).get("message").asText());
        assertEquals("XXX", json(response).get("value").asText());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/currency", "/currency/", "/currency/%20"})
    void shouldReturnBadRequestWhenCurrencyCodeIsMissing(String path) {
        assertError(server.get(path), HttpServletResponse.SC_BAD_REQUEST, "Missing currency code");
        verify(currencyService, never()).findById(any());
    }

    @Test
    void shouldPersistNewCurrency() {
        when(currencyService.persist(any())).thenReturn(currency("PLN", "Polish Zloty", "zl"));

        var response = server.post("/currencies", PLN);
        assertEquals(HttpServletResponse.SC_CREATED, response.statusCode());
        assertCurrency(json(response), "PLN", "Polish Zloty", "zl");
    }

    @Test
    void shouldNotPersistCurrency() {
        when(currencyService.persist(any())).thenThrow(new PersistenceException());

        assertEquals(HttpServletResponse.SC_CONFLICT, server.post("/currencies", PLN).statusCode());
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

    private static void assertCurrency(JsonNode json, String code, String name, String symbol) {
        assertEquals(code, json.get("code").asText());
        assertEquals(name, json.get("name").asText());
        assertEquals(symbol, json.get("symbol").asText());
    }
}
