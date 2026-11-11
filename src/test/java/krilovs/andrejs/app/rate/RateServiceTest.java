package krilovs.andrejs.app.rate;

import jakarta.persistence.PersistenceException;
import krilovs.andrejs.app.currency.Currency;
import krilovs.andrejs.app.error.NoEntityException;
import krilovs.andrejs.app.util.AbstractTestClass;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.postgresql.util.PSQLException;
import org.postgresql.util.PSQLState;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class RateServiceTest extends AbstractTestClass {
    private List<Rate> rates = new ArrayList<>();

    @InjectMocks
    private RateService service;

    @BeforeEach
    void setUp() {
        super.load();
        rates = List.of(
                rate("EUR", "TRY", null),
                rate("EUR", "CHF", BigDecimal.ZERO),
                rate("EUR", "PLN", BigDecimal.valueOf(4.389)),
                rate("EUR", "USD", BigDecimal.valueOf(1.1224))
        );
    }

    @Test
    void shouldReturnAvailableRates() {
        givenRates();

        var expected = rates.stream()
                .map(r -> response(r.getBaseCurrency().getCode(), r.getTargetCurrency().getCode(), r.getRate()))
                .toList();
        assertEquals(expected, service.findAll());
    }

    @ParameterizedTest
    @MethodSource("validPairs")
    void shouldFindByPair(String base, String target, BigDecimal rate) {
        givenRates();

        assertEquals(rate, service.findByPair(base, target).orElseThrow().rate());
    }

    @ParameterizedTest
    @MethodSource("invalidPairs")
    void shouldNotFindByPair(String base, String target) {
        givenRates();

        assertTrue(service.findByPair(base, target).isEmpty());
    }

    @Test
    void shouldPersistNewRate() {
        givenNativeQuery();
        when(query.getSingleResult()).thenReturn(rate("BYN", "EUR", BigDecimal.valueOf(2.9)));

        var byn = service.persist(new RateRequest("BYN", "EUR", BigDecimal.valueOf(2.9)));
        assertEquals(response("BYN", "EUR", BigDecimal.valueOf(2.9)), byn);
    }

    @ParameterizedTest
    @MethodSource("persistenceFailures")
    void shouldNotPersistCurrencyRate(PersistenceException failure,
                                      Class<? extends RuntimeException> expectedType,
                                      String expectedMessage) {
        givenNativeQuery();
        when(query.getSingleResult()).thenThrow(failure);

        var request = new RateRequest("BYN", "XXX", BigDecimal.valueOf(2.9));
        var thrown = assertThrows(RuntimeException.class, () -> service.persist(request));
        assertEquals(expectedType, thrown.getClass());
        assertEquals(expectedMessage, thrown.getMessage());
    }

    @Test
    void shouldUpdateRate() {
        var updated = rate("EUR", "TRY", BigDecimal.TEN);
        givenNativeQuery();
        when(query.getSingleResultOrNull()).thenReturn(updated);

        var lira = service.update(new RateRequest("eur", "try", BigDecimal.TEN));
        assertEquals(response("EUR", "TRY", BigDecimal.TEN), lira);
    }

    @Test
    void shouldNotUpdateMissingRate() {
        givenNativeQuery();
        when(query.getSingleResultOrNull()).thenReturn(null);

        var request = new RateRequest("EUR", "TRY", BigDecimal.TEN);
        var exception = assertThrows(NoEntityException.class, () -> service.update(request));
        assertEquals("Exchange rate not found", exception.getMessage());
        assertEquals("EURTRY", exception.getValue());
    }

    private void givenRates() {
        when(em.createQuery(anyString(), eq(Rate.class))).thenReturn(query);
        when(query.getResultList()).thenReturn(rates);
    }

    private void givenNativeQuery() {
        when(em.createNativeQuery(anyString(), eq(Rate.class))).thenReturn(query);
    }

    private static Currency currencyEntity(String code) {
        var currency = new Currency();
        currency.setCode(code);
        return currency;
    }

    private static Rate rate(String baseCurrency, String targetCurrency, BigDecimal currencyRate) {
        var id = new RateId();
        id.setBaseCurrency(baseCurrency);
        id.setTargetCurrency(targetCurrency);

        var rate = new Rate();
        rate.setId(id);
        rate.setRate(currencyRate);
        rate.setBaseCurrency(currencyEntity(baseCurrency));
        rate.setTargetCurrency(currencyEntity(targetCurrency));
        return rate;
    }

    /** Expected rates are written out by hand (13 decimals, as the service calculates), based on EUR->USD 1.1224 and EUR->PLN 4.389. */
    private static Stream<Arguments> validPairs() {
        return Stream.of(
                // direct
                arguments("EUR", "USD", new BigDecimal("1.1224")),
                arguments("EUR", "PLN", new BigDecimal("4.389")),
                // inverse: 1 / 1.1224
                arguments("USD", "EUR", new BigDecimal("0.8909479686386")),
                // cross rate through EUR: 4.389 / 1.1224 and 1.1224 / 4.389
                arguments("USD", "PLN", new BigDecimal("3.9103706343550")),
                arguments("PLN", "USD", new BigDecimal("0.2557302346776"))
        );
    }

    private static Stream<Arguments> invalidPairs() {
        return Stream.of(
                arguments("EUR", "TRY"),
                arguments("EUR", "CHF"),
                arguments("TRY", "EUR"),
                arguments("GBP", "PLN"),
                arguments("USD", "TRY")
        );
    }

    private static Stream<Arguments> persistenceFailures() {
        var foreignKey = new PersistenceException(new PSQLException("violates foreign key constraint", PSQLState.FOREIGN_KEY_VIOLATION));
        var uniqueKey = new PersistenceException(new PSQLException("duplicate key", PSQLState.UNIQUE_VIOLATION));
        var notDatabase = new PersistenceException("connection lost");
        return Stream.of(
                arguments(foreignKey, NoEntityException.class, "Currency not found for rate pair", "foreign key violation"),
                arguments(uniqueKey, PersistenceException.class, uniqueKey.getMessage(), "primary key violation"),
                arguments(notDatabase, PersistenceException.class, "connection lost", "not a database error")
        );
    }
}
