package krilovs.andrejs.app.rate;

import krilovs.andrejs.app.error.NoEntityException;
import krilovs.andrejs.app.util.AbstractTestClass;
import org.eclipse.persistence.dynamic.DynamicEntity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

class RateMapperTest extends AbstractTestClass {
    @Test
    void shouldMapRequestToEntityWithCurrencies() {
        var eur = currencyEntity("EUR");
        var usd = currencyEntity("USD");
        when(em.find(DynamicEntity.class, "EUR")).thenReturn(eur);
        when(em.find(DynamicEntity.class, "USD")).thenReturn(usd);

        var rate = RateMapper.toEntity(new RateRequest("eur", "usd", new BigDecimal("1.12")), persistence, em);

        assertEquals("EUR", rate.get("baseCurrencyCode"));
        assertEquals("USD", rate.get("targetCurrencyCode"));
        assertEquals(new BigDecimal("1.12"), rate.get("rate"));
        assertSame(eur, rate.get("baseCurrency"));
        assertSame(usd, rate.get("targetCurrency"));
    }

    @Test
    void shouldNotMapWhenCurrencyIsMissing() {
        when(em.find(DynamicEntity.class, "EUR")).thenReturn(currencyEntity("EUR"));
        var request = new RateRequest("eur", "xxx", BigDecimal.ONE);

        var exception = assertThrows(NoEntityException.class, () -> RateMapper.toEntity(request, persistence, em));
        assertEquals("Currency not found for rate pair", exception.getMessage());
        assertEquals(request, exception.getValue());
    }

    @Test
    void shouldMapEntityToResponse() {
        var rate = rateEntity("EUR", "USD", new BigDecimal("1.1224"));
        assertEquals(response("EUR", "USD", new BigDecimal("1.1224")), RateMapper.toResponse(rate));
    }
}
