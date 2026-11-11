package krilovs.andrejs.app.exchange;

import krilovs.andrejs.app.error.NoEntityException;
import krilovs.andrejs.app.rate.RateService;
import krilovs.andrejs.app.util.AbstractTestClass;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExchangeServiceTest extends AbstractTestClass {
    @Mock
    private RateService rateService;

    @InjectMocks
    private ExchangeService service;

    @Test
    void shouldConvertAmount() {
        when(rateService.findByPair("EUR", "USD"))
                .thenReturn(Optional.of(rate("EUR", "USD", BigDecimal.valueOf(1.12))));

        var exchangeRequest = new ExchangeRequest("EUR", "USD", BigDecimal.valueOf(100));
        var response = service.convert(exchangeRequest);
        assertEquals(new BigDecimal("1.12"), response.rate());
        assertEquals(new BigDecimal("100.00"), response.amount());
        assertEquals(new BigDecimal("112.00"), response.convertedAmount());
    }

    @Test
    void shouldNotConvertAmountWithNullRate() {
        when(rateService.findByPair("EUR", "USD"))
                .thenReturn(Optional.of(rate("EUR", "USD", null)));

        var exchangeRequest = new ExchangeRequest("EUR", "USD", BigDecimal.valueOf(1.12));
        var exception = assertThrows(NoEntityException.class, () -> service.convert(exchangeRequest));
        assertEquals("Exchange pair not found", exception.getMessage());
        assertEquals(List.of("EUR", "USD"), exception.getValue());
    }
}
