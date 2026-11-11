package krilovs.andrejs.app.exchange;

import krilovs.andrejs.app.error.NoEntityException;
import krilovs.andrejs.app.rate.RateService;
import krilovs.andrejs.app.util.AbstractTestClass;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

class ExchangeServiceTest extends AbstractTestClass {
    @Mock
    private RateService rateService;

    @InjectMocks
    private ExchangeService service;

    @Test
    void shouldConvertAmount() {
        givenRate(BigDecimal.valueOf(1.12));

        var exchangeRequest = new ExchangeRequest("EUR", "USD", BigDecimal.valueOf(100));
        var response = service.convert(exchangeRequest);
        assertEquals(new BigDecimal("1.12"), response.rate());
        assertEquals(new BigDecimal("100.00"), response.amount());
        assertEquals(new BigDecimal("112.00"), response.convertedAmount());
    }

    @Test
    void shouldNotConvertAmountWithNullRate() {
        givenRate(null);

        var exchangeRequest = new ExchangeRequest("EUR", "USD", BigDecimal.valueOf(1.12));
        var exception = assertThrows(NoEntityException.class, () -> service.convert(exchangeRequest));
        assertEquals("Exchange pair not found", exception.getMessage());
        assertEquals(List.of("EUR", "USD"), exception.getValue());
    }

    private void givenRate(BigDecimal rate) {
        when(rateService.findByPair("EUR", "USD")).thenReturn(Optional.of(response("EUR", "USD", rate)));
    }
}
