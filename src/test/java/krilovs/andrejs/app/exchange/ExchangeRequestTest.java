package krilovs.andrejs.app.exchange;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExchangeRequestTest {
    @Test
    void shouldReportMissingBaseCurrencyOnlyOnce() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var violations = factory.getValidator().validate(new ExchangeRequest(null, "USD", BigDecimal.TEN));

            assertEquals(1, violations.size());
            var violation = violations.iterator().next();
            assertEquals("baseCurrencyCode", violation.getPropertyPath().toString());
            assertEquals("Base currency code should be defined", violation.getMessage());
        }
    }
}
