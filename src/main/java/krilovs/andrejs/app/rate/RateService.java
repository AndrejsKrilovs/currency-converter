package krilovs.andrejs.app.rate;

import jakarta.persistence.PersistenceException;
import krilovs.andrejs.app.config.PersistenceConfig;
import krilovs.andrejs.app.error.NoEntityException;
import lombok.RequiredArgsConstructor;
import org.eclipse.persistence.config.QueryHints;
import org.postgresql.util.PSQLException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
public class RateService {
    private final static String PIVOT_CURRENCY = "EUR";
    private final static RateMapper MAPPER = RateMapper.INSTANCE;
    private final PersistenceConfig persistence;

    public List<RateResponse> findAll() {
        var sql = "SELECT r FROM Rate r";
        return persistence.withEntityManager(em -> {
            var queryResult = em.createQuery(sql, Rate.class)
                    .setHint(QueryHints.LEFT_FETCH, "r.baseCurrency")
                    .setHint(QueryHints.LEFT_FETCH, "r.targetCurrency")
                    .getResultList();
            return queryResult.stream()
                    .map(MAPPER::toRateResponse)
                    .toList();
        });
    }

    public Optional<RateResponse> findByPair(String base, String target) {
        var from = base.toUpperCase();
        var to = target.toUpperCase();
        var sql = """
            SELECT r FROM Rate r
            WHERE (r.id.baseCurrency = :base AND r.id.targetCurrency = :target)
               OR (r.id.baseCurrency = :target AND r.id.targetCurrency = :base)
               OR (r.id.baseCurrency = :pivot AND r.id.targetCurrency IN (:target, :base))
               OR (r.id.baseCurrency IN (:target, :base) AND r.id.targetCurrency = :pivot)
            """;
        var rates = persistence.withEntityManager(em ->
                em.createQuery(sql, Rate.class)
                        .setHint(QueryHints.LEFT_FETCH, "r.baseCurrency")
                        .setHint(QueryHints.LEFT_FETCH, "r.targetCurrency")
                        .setParameter("base", from)
                        .setParameter("target", to)
                        .setParameter("pivot", PIVOT_CURRENCY)
                        .getResultList()
        );
        var currentRate = pickCorrectRate(rates, from, to).filter(RateService::hasValue)
                .or(() -> pickCorrectRate(rates, to, from).flatMap(this::invertRate))
                .or(() -> crossViaPivot(rates, from, to));
        return currentRate.map(MAPPER::toRateResponse);
    }

    public RateResponse persist(RateRequest request) {
        var sql = """
            INSERT INTO rate (base_currency, target_currency, rate)
            VALUES (?1, ?2, ?3)
            RETURNING base_currency, target_currency, rate""";
        return persistence.inTransaction(em -> {
            try {
                var insertedItem = (Rate) em.createNativeQuery(sql, Rate.class)
                        .setParameter(1, request.baseCurrencyCode().toUpperCase())
                        .setParameter(2, request.targetCurrencyCode().toUpperCase())
                        .setParameter(3, request.rate())
                        .getSingleResult();
                return MAPPER.toRateResponse(insertedItem);
            }
            catch (PersistenceException e) {
                // FK violation: one of the currencies does not exist in the currency table
                if (findCause(e, PSQLException.class).filter(pg -> "23503".equals(pg.getSQLState())).isPresent()) {
                    throw new NoEntityException("Currency not found for rate pair", request);
                }
                throw e;
            }
        });
    }

    public RateResponse update(RateRequest request) {
        var base = request.baseCurrencyCode().toUpperCase();
        var target = request.targetCurrencyCode().toUpperCase();
        var sql = """
            UPDATE rate SET rate = ?3
            WHERE base_currency = ?1 AND target_currency = ?2
            RETURNING base_currency, target_currency, rate""";
        var rate = persistence.inTransaction(em ->
                (Rate) em.createNativeQuery(sql, Rate.class)
                        .setParameter(1, base)
                        .setParameter(2, target)
                        .setParameter(3, request.rate())
                        .getSingleResultOrNull()
        );
        if (rate == null) {
            throw new NoEntityException("Exchange rate not found", base + target);
        }
        return MAPPER.toRateResponse(rate);
    }

    private static Optional<Rate> pickCorrectRate(List<Rate> rates, String base, String target) {
        return rates.stream()
                .filter(r -> r.getId().getBaseCurrency().equals(base) && r.getId().getTargetCurrency().equals(target))
                .findFirst();
    }

    private static Boolean hasValue(Rate rate) {
        return rate.getRate() != null && rate.getRate().signum() != 0;
    }

    private Optional<Rate> toPivotRate(List<Rate> rates, String code) {
        return pickCorrectRate(rates, PIVOT_CURRENCY, code)
                .or(() -> pickCorrectRate(rates, code, PIVOT_CURRENCY).flatMap(this::invertRate))
                .filter(RateService::hasValue);
    }

    private Optional<Rate> crossViaPivot(List<Rate> rates, String base, String target) {
        var baseCurrencyRate = toPivotRate(rates, base).orElse(null);
        var targetCurrencyRate = toPivotRate(rates, target).orElse(null);
        if (baseCurrencyRate == null || targetCurrencyRate == null) {
            return Optional.empty();
        }

        var id = new RateId();
        id.setBaseCurrency(base);
        id.setTargetCurrency(target);

        var crossRate = new Rate();
        crossRate.setId(id);
        crossRate.setBaseCurrency(baseCurrencyRate.getTargetCurrency());
        crossRate.setTargetCurrency(targetCurrencyRate.getTargetCurrency());

        var calculatedRate = targetCurrencyRate.getRate().divide(baseCurrencyRate.getRate(), 13, RoundingMode.HALF_UP);
        crossRate.setRate(calculatedRate);
        return Optional.of(crossRate);
    }

    private Optional<Rate> invertRate(Rate rate) {
        if (!hasValue(rate)) {
            return Optional.empty();
        }

        var id = new RateId();
        id.setBaseCurrency(rate.getId().getTargetCurrency());
        id.setTargetCurrency(rate.getId().getBaseCurrency());

        var inverse = new Rate();
        inverse.setId(id);
        inverse.setBaseCurrency(rate.getTargetCurrency());
        inverse.setTargetCurrency(rate.getBaseCurrency());
        inverse.setRate(BigDecimal.ONE.divide(rate.getRate(), 13, RoundingMode.HALF_UP));
        return Optional.of(inverse);
    }

    private static <T extends Throwable> Optional<T> findCause(Throwable throwable, Class<T> targetClass) {
        Throwable current = throwable;
        while (current != null) {
            if (targetClass.isInstance(current)) {
                return Optional.of((T) current);
            }
            current = current.getCause();
        }
        return Optional.empty();
    }
}
