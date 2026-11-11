package krilovs.andrejs.app.currency;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CurrencyRequest(
        @NotBlank(message = "Currency code should be defined")
        @Size(min = 3, max = 3, message = "Currency code must contain 3 characters")
        String code,

        @NotBlank(message = "Currency name be defined")
        String name,

        String symbol
) { }
