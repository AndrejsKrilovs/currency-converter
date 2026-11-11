package krilovs.andrejs.app.currency;

import krilovs.andrejs.app.util.AbstractTestClass;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

class CurrencyServiceTest extends AbstractTestClass {
    private List<Currency> currencies;

    @InjectMocks
    private CurrencyService service;

    @BeforeEach
    void setUp() {
        super.load();
        currencies = List.of(entity("EUR", "Euro", "€"), entity("USD", "US Dollar", "$"));
    }

    @Test
    void shouldReturnAvailableCurrencies() {
        when(em.createQuery(anyString(), eq(Currency.class)).getResultList()).thenReturn(currencies);
        assertEquals(
                List.of(currency("EUR", "Euro", "€"), currency("USD", "US Dollar", "$")),
                service.findAll()
        );
    }

    @Test
    void shouldFindAvailableCurrency() {
        when(em.find(eq(Currency.class), anyString())).thenReturn(currencies.getFirst());
        assertEquals(currency("EUR", "Euro", "€"), service.findById("EUR").orElseThrow());
    }

    @Test
    void shouldNotFindMissingCurrency() {
        when(em.find(eq(Currency.class), anyString())).thenReturn(null);
        assertTrue(service.findById("XXX").isEmpty());
    }

    @Test
    void shouldPersistNewCurrency() {
        var response = service.persist(new CurrencyRequest("RUB", "Russian Ruble", "₽"));
        assertEquals(currency("RUB", "Russian Ruble", "₽"), response);

        var persisted = ArgumentCaptor.forClass(Currency.class);
        var order = inOrder(em);
        order.verify(em).persist(persisted.capture());
        order.verify(em).flush();
        assertEquals("RUB", persisted.getValue().getCode());
        assertEquals("Russian Ruble", persisted.getValue().getName());
        assertEquals("₽", persisted.getValue().getSymbol());
    }

    private static Currency entity(String code, String name, String symbol) {
        var currency = new Currency();
        currency.setCode(code);
        currency.setName(name);
        currency.setSymbol(symbol);
        return currency;
    }
}
