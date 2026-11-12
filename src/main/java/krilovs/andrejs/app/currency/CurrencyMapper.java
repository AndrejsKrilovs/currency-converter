package krilovs.andrejs.app.currency;

import krilovs.andrejs.app.config.jpa.PersistenceConfig;
import lombok.experimental.UtilityClass;
import org.eclipse.persistence.dynamic.DynamicEntity;

import static krilovs.andrejs.app.util.DynamicEntities.CURRENCY;

/** Dynamic entities have no getters, so MapStruct has nothing to map: the properties are read by name. */
@UtilityClass
public class CurrencyMapper {
    public static CurrencyResponse toResponse(DynamicEntity currency) {
        return new CurrencyResponse(currency.get("code"), currency.get("name"), currency.get("symbol"));
    }

    public static DynamicEntity toEntity(CurrencyRequest request, PersistenceConfig persistence) {
        return persistence.newEntity(CURRENCY)
                .set("code", request.code())
                .set("name", request.name())
                .set("symbol", request.symbol());
    }
}
