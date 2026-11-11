package krilovs.andrejs.app.util;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import krilovs.andrejs.app.config.PersistenceConfig;
import krilovs.andrejs.app.currency.Currency;
import krilovs.andrejs.app.currency.CurrencyResponse;
import krilovs.andrejs.app.rate.Rate;
import krilovs.andrejs.app.rate.RateResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.function.Function;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
public abstract class AbstractTestClass {
    @Mock
    protected PersistenceConfig persistence;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    protected EntityManager em;

    @Mock(answer = Answers.RETURNS_SELF)
    protected TypedQuery<Rate> query;

    @BeforeEach
    protected void stubPersistence() {
        lenient().when(persistence.withEntityManager(any()))
                .thenAnswer(call -> call.<Function<EntityManager, Object>>getArgument(0).apply(em));
        lenient().when(persistence.inTransaction(any()))
                .thenAnswer(call -> call.<Function<EntityManager, Object>>getArgument(0).apply(em));
    }

    protected static Currency currencyEntity(String code) {
        return currencyEntity(code, null, null);
    }

    protected static Currency currencyEntity(String code, String name, String symbol) {
        var currency = new Currency();
        currency.setCode(code);
        currency.setName(name);
        currency.setSymbol(symbol);
        return currency;
    }

    protected static CurrencyResponse currency(String code) {
        return new CurrencyResponse(code, null, null);
    }

    protected static CurrencyResponse currency(String code, String name, String symbol) {
        return new CurrencyResponse(code, name, symbol);
    }

    protected static RateResponse response(String baseCurrency, String targetCurrency, BigDecimal currencyRate) {
        return new RateResponse(currency(baseCurrency), currency(targetCurrency), currencyRate);
    }
}
