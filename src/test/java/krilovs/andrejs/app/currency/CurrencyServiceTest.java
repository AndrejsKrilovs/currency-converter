package krilovs.andrejs.app.currency;

import krilovs.andrejs.app.util.AbstractTestClass;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrencyServiceTest extends AbstractTestClass {
    private List<Currency> currencies = new ArrayList<>();

    @InjectMocks
    private CurrencyService service;

    @BeforeEach
    void setUp() {
        super.load();
        currencies = List.of(currency("EUR"), currency("USD"));
    }

    @Test
    void shouldReturnAvailableCurrencies() {
        when(em.createQuery(anyString(), eq(Currency.class)).getResultList()).thenReturn(currencies);
        assertFalse(service.findAll().isEmpty());
    }

    @Test
    void shouldFindAvailableCurrency() {
        when(em.find(eq(Currency.class), anyString())).thenReturn(currencies.getFirst());
        assertFalse(service.findById("EUR").isEmpty());
    }

    @Test
    void shouldPersistNewCurrency() {
        var rub = currency("RUB");
        assertSame(rub, service.persist(rub));

        var order = inOrder(em);
        order.verify(em).persist(rub);
        order.verify(em).flush();
    }
}
