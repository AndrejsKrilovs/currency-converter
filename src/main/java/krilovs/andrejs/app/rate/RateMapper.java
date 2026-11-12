package krilovs.andrejs.app.rate;

import jakarta.persistence.EntityManager;
import krilovs.andrejs.app.config.PersistenceConfig;
import krilovs.andrejs.app.currency.CurrencyMapper;
import krilovs.andrejs.app.error.NoEntityException;
import lombok.experimental.UtilityClass;
import org.eclipse.persistence.dynamic.DynamicEntity;

import static krilovs.andrejs.app.util.DynamicEntities.CURRENCY;
import static krilovs.andrejs.app.util.DynamicEntities.RATE;

@UtilityClass
public class RateMapper {
    public static RateResponse toResponse(DynamicEntity rate) {
        return new RateResponse(
                CurrencyMapper.toResponse(rate.get("baseCurrency")),
                CurrencyMapper.toResponse(rate.get("targetCurrency")),
                rate.get("rate")
        );
    }

    /** Key, value and both currencies (found through the EntityManager, i.e. the shared cache); both must exist. */
    public static DynamicEntity toEntity(RateRequest request, PersistenceConfig persistence, EntityManager em) {
        var base = request.baseCurrencyCode().toUpperCase();
        var target = request.targetCurrencyCode().toUpperCase();
        DynamicEntity baseCurrency = em.find(persistence.entityClass(CURRENCY), base);
        DynamicEntity targetCurrency = em.find(persistence.entityClass(CURRENCY), target);
        if (baseCurrency == null || targetCurrency == null) {
            throw new NoEntityException("Currency not found for rate pair", request);
        }

        return persistence.newEntity(RATE)
                .set("baseCurrencyCode", base)
                .set("targetCurrencyCode", target)
                .set("baseCurrency", baseCurrency)
                .set("targetCurrency", targetCurrency)
                .set("rate", request.rate());
    }
}
