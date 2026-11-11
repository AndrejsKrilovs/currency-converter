package krilovs.andrejs.app.rate;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record UpdateRateRequest(
        @NotNull(message = "Rate should be defined")
        @Positive(message = "Rate must be a positive number")
        BigDecimal rate) {
}
