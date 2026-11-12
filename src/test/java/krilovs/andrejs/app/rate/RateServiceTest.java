package krilovs.andrejs.app.rate;

import jakarta.persistence.PersistenceException;
import krilovs.andrejs.app.currency.Currency;
import krilovs.andrejs.app.error.NoEntityException;
import krilovs.andrejs.app.util.AbstractTestClass;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.postgresql.util.PSQLException;
import org.postgresql.util.PSQLState;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RateServiceTest extends AbstractTestClass {
    private static final RateRequest BYN_EUR = new RateRequest("BYN", "EUR", BigDecimal.valueOf(2.9));

    /** The rates in the database: no value (TRY), zero (CHF) and the two EUR rates the calculations are based on. */
    private final List<RateResponse> responses = List.of(
            response("EUR", "TRY", null),
            response("EUR", "CHF", BigDecimal.ZERO),
            response("EUR", "PLN", BigDecimal.valueOf(4.389)),
            response("EUR", "USD", BigDecimal.valueOf(1.1224))
    );

    private final List<Rate> rates = responses.stream()
            .map(r -> rate(r.baseCurrency().code(), r.targetCurrency().code(), r.rate()))
            .toList();

    @InjectMocks
    private RateService service;

    @Test
    void shouldReturnAvailableRates() {
        givenRates();

        assertEquals(responses, service.findAll());
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
        givenCurrencies();

        var byn = service.persist(BYN_EUR);
        assertEquals(response("BYN", "EUR", BigDecimal.valueOf(2.9)), byn);

        var persisted = ArgumentCaptor.forClass(Rate.class);
        verify(em).persist(persisted.capture());
        verify(em).flush();
        assertEquals("BYN", persisted.getValue().getId().getBaseCurrency());
        assertEquals("EUR", persisted.getValue().getId().getTargetCurrency());
    }

    @Test
    void shouldNotPersistRateWhenCurrencyIsMissing() {
        when(em.find(eq(Currency.class), anyString())).thenReturn(null);

        var exception = assertThrows(NoEntityException.class, () -> service.persist(BYN_EUR));
        assertEquals("Currency not found for rate pair", exception.getMessage());
        assertEquals(BYN_EUR, exception.getValue());
        verify(em, never()).persist(any());
    }

    @Test
    void shouldNotPersistExistingRate() {
        givenCurrencies();
        var duplicate = new PersistenceException(new PSQLException("duplicate key", PSQLState.UNIQUE_VIOLATION));
        doThrow(duplicate).when(em).flush();

        var exception = assertThrows(NoEntityException.class, () -> service.persist(BYN_EUR));
        assertEquals("Rate already exists", exception.getMessage());
    }

    @Test
    void shouldNotPersistRateWhenDatabaseFails() {
        givenCurrencies();
        var failure = new PersistenceException("connection lost");
        doThrow(failure).when(em).flush();

        assertSame(failure, assertThrows(PersistenceException.class, () -> service.persist(BYN_EUR)));
    }

    @Test
    void shouldUpdateRate() {
        givenCurrencies();
        givenUpdatedRows(1);

        var lira = service.update(new RateRequest("eur", "try", BigDecimal.TEN));
        assertEquals(response("EUR", "TRY", BigDecimal.TEN), lira);
        verify(query).setParameter("rate", BigDecimal.TEN);
        verify(query).setParameter("base", "EUR");
        verify(query).setParameter("target", "TRY");
    }

    @Test
    void shouldNotUpdateMissingRate() {
        givenCurrencies();
        givenUpdatedRows(0);

        var request = new RateRequest("EUR", "TRY", BigDecimal.TEN);
        var exception = assertThrows(NoEntityException.class, () -> service.update(request));
        assertEquals("Exchange rate not found", exception.getMessage());

        var rate = new RateId();
        rate.setBaseCurrency("EUR");
        rate.setTargetCurrency("TRY");
        assertEquals(rate, exception.getValue());
    }

    private void givenRates() {
        when(em.createQuery(anyString(), eq(Rate.class))).thenReturn(query);
        when(query.getResultList()).thenReturn(rates);
    }

    /** Every requested currency exists, as in the warmed-up shared cache. */
    private void givenCurrencies() {
        when(em.find(eq(Currency.class), anyString())).thenAnswer(call -> currencyEntity(call.getArgument(1)));
    }

    private void givenUpdatedRows(int rows) {
        when(em.createQuery(anyString())).thenReturn(query);
        when(query.executeUpdate()).thenReturn(rows);
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
}
