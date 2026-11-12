package krilovs.andrejs.app.util;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import krilovs.andrejs.app.config.jpa.PersistenceConfig;
import krilovs.andrejs.app.currency.CurrencyResponse;
import krilovs.andrejs.app.rate.RateResponse;
import org.eclipse.persistence.dynamic.DynamicEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.function.Function;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
public abstract class AbstractTestClass {
    @Mock
    protected PersistenceConfig persistence;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    protected EntityManager em;

    @Mock(answer = Answers.RETURNS_SELF)
    protected TypedQuery<DynamicEntity> query;

    @BeforeEach
    protected void stubPersistence() {
        lenient().when(persistence.withEntityManager(any()))
                .thenAnswer(call -> call.<Function<EntityManager, Object>>getArgument(0).apply(em));
        lenient().when(persistence.inTransaction(any()))
                .thenAnswer(call -> call.<Function<EntityManager, Object>>getArgument(0).apply(em));
        lenient().when(persistence.entityClass(anyString())).thenAnswer(call -> DynamicEntity.class);
        lenient().when(persistence.newEntity(anyString())).thenAnswer(call -> new MapEntity());
    }

    protected static DynamicEntity currencyEntity(String code) {
        return currencyEntity(code, null, null);
    }

    protected static DynamicEntity currencyEntity(String code, String name, String symbol) {
        return new MapEntity()
                .set("code", code)
                .set("name", name)
                .set("symbol", symbol);
    }

    protected static DynamicEntity rateEntity(String baseCurrency, String targetCurrency, BigDecimal currencyRate) {
        return new MapEntity()
                .set("baseCurrencyCode", baseCurrency)
                .set("targetCurrencyCode", targetCurrency)
                .set("baseCurrency", currencyEntity(baseCurrency))
                .set("targetCurrency", currencyEntity(targetCurrency))
                .set("rate", currencyRate);
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
