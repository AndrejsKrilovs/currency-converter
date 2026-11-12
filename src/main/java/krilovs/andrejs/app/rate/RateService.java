package krilovs.andrejs.app.rate;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.TypedQuery;
import krilovs.andrejs.app.config.jpa.PersistenceConfig;
import krilovs.andrejs.app.error.NoEntityException;
import lombok.RequiredArgsConstructor;
import org.eclipse.persistence.config.QueryHints;
import org.eclipse.persistence.dynamic.DynamicEntity;
import org.postgresql.util.PSQLException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RequiredArgsConstructor
public class RateService {
    private static final String PIVOT_CURRENCY = "EUR";
    private static final String UNIQUE_VIOLATION = "23505";
    private static final int CALCULATED_SCALE = 13;
    private final PersistenceConfig persistence;

    public List<RateResponse> findAll() {
        return persistence.withEntityManager(em -> rates(em, "SELECT r FROM Rate r").getResultList().stream()
                .map(RateMapper::toResponse)
                .toList());
    }

    /** Direct rate, else the inverse of the opposite one, else a cross rate through {@value PIVOT_CURRENCY}. */
    public Optional<RateResponse> findByPair(String base, String target) {
        var from = base.toUpperCase();
        var to = target.toUpperCase();
        var sql = """
            SELECT r FROM Rate r
            WHERE (r.baseCurrencyCode = :base AND r.targetCurrencyCode = :target)
               OR (r.baseCurrencyCode = :target AND r.targetCurrencyCode = :base)
               OR (r.baseCurrencyCode = :pivot AND r.targetCurrencyCode IN (:target, :base))
               OR (r.baseCurrencyCode IN (:target, :base) AND r.targetCurrencyCode = :pivot)
            """;
        var rates = persistence.withEntityManager(em -> rates(em, sql)
                .setParameter("base", from)
                .setParameter("target", to)
                .setParameter("pivot", PIVOT_CURRENCY)
                .getResultList().stream()
                .map(RateMapper::toResponse)
                .toList());
        return find(rates, from, to).filter(RateService::hasValue)
                .or(() -> find(rates, to, from).flatMap(RateService::invert))
                .or(() -> cross(rates, from, to));
    }

    public RateResponse persist(RateRequest request) {
        return persistence.inTransaction(em -> {
            var rate = RateMapper.toEntity(request, persistence, em);
            try {
                em.persist(rate);
                em.flush();
            }
            catch (PersistenceException e) {
                if (findCause(e, PSQLException.class).filter(pg -> UNIQUE_VIOLATION.equals(pg.getSQLState())).isPresent()) {
                    throw new NoEntityException("Rate already exists", pair(rate));
                }
                throw e;
            }
            return RateMapper.toResponse(rate);
        });
    }

    /** One UPDATE statement: merge() would SELECT the row first, and a missing row is detected by the update count. */
    public RateResponse update(RateRequest request) {
        var jpql = """
            UPDATE Rate r SET r.rate = :rate
            WHERE r.baseCurrencyCode = :base AND r.targetCurrencyCode = :target
            """;
        return persistence.inTransaction(em -> {
            var rate = RateMapper.toEntity(request, persistence, em);
            var pair = pair(rate);
            var updated = em.createQuery(jpql)
                    .setParameter("rate", rate.get("rate"))
                    .setParameter("base", pair.get("baseCurrency"))
                    .setParameter("target", pair.get("targetCurrency"))
                    .executeUpdate();
            if (updated == 0) {
                throw new NoEntityException("Exchange rate not found", pair);
            }
            return RateMapper.toResponse(rate);
        });
    }

    /** Rate rows with both currencies fetched, because the entities are used after the EntityManager is closed. */
    private static TypedQuery<DynamicEntity> rates(EntityManager em, String jpql) {
        return em.createQuery(jpql, DynamicEntity.class)
                .setHint(QueryHints.LEFT_FETCH, "r.baseCurrency")
                .setHint(QueryHints.LEFT_FETCH, "r.targetCurrency");
    }

    private static Map<String, String> pair(DynamicEntity rate) {
        return Map.of("baseCurrency", rate.get("baseCurrencyCode"), "targetCurrency", rate.get("targetCurrencyCode"));
    }

    private static Optional<RateResponse> find(List<RateResponse> rates, String base, String target) {
        return rates.stream()
                .filter(r -> r.baseCurrency().code().equals(base) && r.targetCurrency().code().equals(target))
                .findFirst();
    }

    private static boolean hasValue(RateResponse rate) {
        return rate.rate() != null && rate.rate().signum() != 0;
    }

    private static Optional<RateResponse> invert(RateResponse rate) {
        return Optional.of(rate).filter(RateService::hasValue)
                .map(r -> new RateResponse(
                        r.targetCurrency(), r.baseCurrency(),
                        BigDecimal.ONE.divide(r.rate(), CALCULATED_SCALE, RoundingMode.HALF_UP)
                ));
    }

    /** Rate of the currency against the pivot, taken from the opposite direction if needed. */
    private static Optional<RateResponse> againstPivot(List<RateResponse> rates, String code) {
        return find(rates, PIVOT_CURRENCY, code)
                .or(() -> find(rates, code, PIVOT_CURRENCY).flatMap(RateService::invert))
                .filter(RateService::hasValue);
    }

    private static Optional<RateResponse> cross(List<RateResponse> rates, String base, String target) {
        return againstPivot(rates, base).flatMap(pivotToBase ->
                againstPivot(rates, target).map(pivotToTarget -> new RateResponse(
                        pivotToBase.targetCurrency(), pivotToTarget.targetCurrency(),
                        pivotToTarget.rate().divide(pivotToBase.rate(), CALCULATED_SCALE, RoundingMode.HALF_UP)
                )));
    }

    private static <T extends Throwable> Optional<T> findCause(Throwable throwable, Class<T> targetClass) {
        for (var current = throwable; current != null; current = current.getCause()) {
            if (targetClass.isInstance(current)) {
                return Optional.of(targetClass.cast(current));
            }
        }
        return Optional.empty();
    }
}
