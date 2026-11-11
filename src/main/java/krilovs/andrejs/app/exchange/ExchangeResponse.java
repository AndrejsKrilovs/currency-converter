package krilovs.andrejs.app.exchange;

import krilovs.andrejs.app.currency.Currency;

import java.math.BigDecimal;

public record ExchangeResponse(
        Currency baseCurrency,
        Currency targetCurrency,
        BigDecimal rate,
        BigDecimal amount,
        BigDecimal convertedAmount
) {}
