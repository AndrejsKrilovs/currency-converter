package krilovs.andrejs.app.util;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import krilovs.andrejs.app.config.PersistenceConfig;
import krilovs.andrejs.app.currency.Currency;
import krilovs.andrejs.app.rate.Rate;
import krilovs.andrejs.app.rate.RateId;
import org.mockito.Answers;
import org.mockito.Mock;

import java.math.BigDecimal;
import java.util.function.Function;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

public abstract class AbstractTestClass {
    @Mock
    protected PersistenceConfig persistence;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    protected EntityManager em;

    @Mock(answer = Answers.RETURNS_SELF)
    protected TypedQuery<Rate> query;

    protected void load() {
        lenient().when(persistence.withEntityManager(any()))
                .thenAnswer(call -> call.<Function<EntityManager, Object>>getArgument(0).apply(em));
        lenient().when(persistence.inTransaction(any()))
                .thenAnswer(call -> call.<Function<EntityManager, Object>>getArgument(0).apply(em));
    }

    protected static Currency currency(String code) {
        var currency = new Currency();
        currency.setCode(code);
        return currency;
    }

    protected static Currency currency(String code, String name, String symbol) {
        var currency = new Currency();
        currency.setCode(code);
        currency.setName(name);
        currency.setSymbol(symbol);
        return currency;
    }

    protected static Rate rate(String baseCurrency, String targetCurrency, BigDecimal currencyRate) {
        var id = new RateId();
        id.setBaseCurrency(baseCurrency);
        id.setTargetCurrency(targetCurrency);

        var rate = new Rate();
        rate.setId(id);
        rate.setRate(currencyRate);
        rate.setBaseCurrency(currency(baseCurrency));
        rate.setTargetCurrency(currency(targetCurrency));
        return rate;
    }
}
