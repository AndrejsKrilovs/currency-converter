package krilovs.andrejs.app.rate;

import lombok.experimental.UtilityClass;
import org.eclipse.persistence.annotations.CacheIsolationType;
import org.eclipse.persistence.dynamic.DynamicClassLoader;
import org.eclipse.persistence.dynamic.DynamicType;
import org.eclipse.persistence.jpa.dynamic.JPADynamicTypeBuilder;

import java.math.BigDecimal;

import static krilovs.andrejs.app.util.DynamicEntities.build;
import static krilovs.andrejs.app.util.DynamicEntities.builder;

/** Dynamic entity of the rate table; its key is the pair of currency columns. */
@UtilityClass
public class Rate {
    public static DynamicType define(DynamicClassLoader loader, DynamicType currency) {
        var builder = builder(loader, Rate.class, "rate", "base_currency", "target_currency");
        builder.addDirectMapping("baseCurrencyCode", String.class, "base_currency");
        builder.addDirectMapping("targetCurrencyCode", String.class, "target_currency");
        builder.addDirectMapping("rate", BigDecimal.class, "rate");
        relation(builder, "baseCurrency", currency, "base_currency");
        relation(builder, "targetCurrency", currency, "target_currency");
        // Rates are written with SQL that bypasses the cache, so they must never sit in the shared cache
        return build(builder, Rate.class, CacheIsolationType.ISOLATED);
    }

    /**
     * The key columns are already written by the direct mappings, so the relation is read-only.
     * No indirection: entities are used after the EntityManager is closed.
     */
    private static void relation(JPADynamicTypeBuilder builder, String name, DynamicType currency, String column) {
        var mapping = builder.addOneToOneMapping(name, currency, column);
        mapping.setIsReadOnly(true);
        mapping.dontUseIndirection();
    }
}
