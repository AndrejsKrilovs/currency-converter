package krilovs.andrejs.app.currency;

import jakarta.persistence.PersistenceException;
import jakarta.servlet.http.HttpServletResponse;
import krilovs.andrejs.app.util.AbstractTestClass;
import krilovs.andrejs.app.util.HttpTestServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static krilovs.andrejs.app.util.HttpTestServer.json;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrencyServletTest extends AbstractTestClass {
    @Mock
    private CurrencyService currencyService;

    private HttpTestServer server;

    @BeforeEach
    void startServer() {
        server = new HttpTestServer(new CurrencyServlet(currencyService), "/currencies", "/currency/*", "/other");
    }

    @AfterEach
    void stopServer() {
        server.close();
    }

    @Test
    void shouldReturnAllCurrencies() {
        var items = List.of(currency("EUR", "Euro", "€"), currency("USD", "US Dollar", "$"));
        when(currencyService.findAll()).thenReturn(items);

        var response = server.get("/currencies");
        assertEquals(HttpServletResponse.SC_OK, response.statusCode());
        assertFalse(json(response).isEmpty());
    }

    @Test
    void shouldReturnCurrencyByCode() {
        when(currencyService.findById(anyString())).thenReturn(Optional.of(currency("EUR", "Euro", "€")));

        var response = server.get("/currency/eur");
        assertEquals(HttpServletResponse.SC_OK, response.statusCode());
        assertFalse(json(response).isEmpty());
        assertEquals("EUR", json(response).get("code").asText());
        assertEquals("Euro", json(response).get("name").asText());
        assertEquals("€", json(response).get("symbol").asText());
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
        var response = server.get(path);
        assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.statusCode());
        assertEquals("Missing currency code", json(response).get("error").asText());
        verify(currencyService, never()).findById(any());
    }

    @Test
    void shouldPersistNewCurrency() {
        when(currencyService.persist(any())).thenReturn(currency("PLN", "Polish Zloty", "zl"));

        var response = server.post("/currencies", """
                {"code": "PLN", "name": "Polish Zloty", "symbol": "zl"}
        """);
        assertEquals(HttpServletResponse.SC_CREATED, response.statusCode());
        assertFalse(json(response).isEmpty());
        assertEquals("PLN", json(response).get("code").asText());
        assertEquals("Polish Zloty", json(response).get("name").asText());
        assertEquals("zl", json(response).get("symbol").asText());
    }

    @Test
    void shouldNotPersistCurrency() {
        when(currencyService.persist(any())).thenThrow(new PersistenceException());

        var response = server.post("/currencies", """
                {"code": "PLN", "name": "Polish Zloty", "symbol": "zl"}
        """);
        assertEquals(HttpServletResponse.SC_CONFLICT, response.statusCode());
    }

    @Test
    void shouldNotProvideIncorrectJson() {
        var response = server.post("/currencies", "");
        assertEquals(HttpServletResponse.SC_NOT_ACCEPTABLE, response.statusCode());
        assertEquals("Incorrect json provided", json(response).get("error").asText());
    }

    @ParameterizedTest
    @CsvSource(value = {"GET,", "POST,{}"})
    void shouldReturnIncorrectEndpointWhenPathIsUnsupported(String method, String body) {
        var response = server.send(method, "/other", body);
        assertEquals(HttpServletResponse.SC_NOT_FOUND, response.statusCode());
        assertEquals("Incorrect endpoint", json(response).get("error").asText());
    }
}
