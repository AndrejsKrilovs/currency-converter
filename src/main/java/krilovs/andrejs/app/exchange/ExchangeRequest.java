package krilovs.andrejs.app.exchange;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ExchangeRequest(
        @NotBlank(message = "Base currency code should be defined")
        @Size(min = 3, max = 3, message = "Currency code must contain 3 characters")
        String baseCurrencyCode,

        @NotBlank(message = "Target currency code should be defined")
        @Size(min = 3, max = 3, message = "Currency code must contain 3 characters")
        String targetCurrencyCode,

        @NotNull(message = "Amount should be defined")
        @Positive(message = "Amount must be a positive number")
        BigDecimal amount) {

    @JsonIgnore
    @SuppressWarnings("unused") // called by Bean Validation via reflection
    @AssertTrue(message = "Base currency must differ from target currency")
    public boolean isCurrenciesDifferent() {
        return baseCurrencyCode == null || !baseCurrencyCode.equalsIgnoreCase(targetCurrencyCode);
    }
}
