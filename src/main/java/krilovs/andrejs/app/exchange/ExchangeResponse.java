package krilovs.andrejs.app.exchange;

import krilovs.andrejs.app.currency.CurrencyResponse;

import java.math.BigDecimal;

public record ExchangeResponse(
        CurrencyResponse baseCurrency,
        CurrencyResponse targetCurrency,
        BigDecimal rate,
        BigDecimal amount,
        BigDecimal convertedAmount
) {}
