package krilovs.andrejs.app.exchange;

import krilovs.andrejs.app.error.NoEntityException;
import krilovs.andrejs.app.rate.RateService;
import lombok.RequiredArgsConstructor;

import java.math.RoundingMode;
import java.util.List;

@RequiredArgsConstructor
public class ExchangeService {
    private final RateService rateService;

    public ExchangeResponse convert(ExchangeRequest request) {
        var rate = rateService.findByPair(request.baseCurrencyCode(), request.targetCurrencyCode())
                .filter(found -> found.getRate() != null)
                .orElseThrow(() -> new NoEntityException(
                        "Exchange pair not found",
                        List.of(request.baseCurrencyCode(), request.targetCurrencyCode())
                ));

        return new ExchangeResponse(
                rate.getBaseCurrency(),
                rate.getTargetCurrency(),
                rate.getRate().stripTrailingZeros(),
                request.amount().setScale(2, RoundingMode.HALF_UP),
                rate.getRate().multiply(request.amount()).setScale(2, RoundingMode.HALF_UP)
        );
    }
}
