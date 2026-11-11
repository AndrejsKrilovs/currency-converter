package krilovs.andrejs.app.rate;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import krilovs.andrejs.app.currency.CurrencyResponse;
import krilovs.andrejs.app.util.PlainDecimalSerializer;

import java.math.BigDecimal;

public record RateResponse(
        CurrencyResponse baseCurrency,
        CurrencyResponse targetCurrency,

        @JsonSerialize(using = PlainDecimalSerializer.class)
        BigDecimal rate
) { }
