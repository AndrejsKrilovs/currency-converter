package krilovs.andrejs.app.currency;

import lombok.experimental.UtilityClass;
import org.eclipse.persistence.annotations.CacheIsolationType;
import org.eclipse.persistence.dynamic.DynamicClassLoader;
import org.eclipse.persistence.dynamic.DynamicType;

import static krilovs.andrejs.app.util.DynamicEntities.build;
import static krilovs.andrejs.app.util.DynamicEntities.builder;

/** Dynamic entity of the currency table. */
@UtilityClass
public class Currency {
    public static DynamicType define(DynamicClassLoader loader) {
        var builder = builder(loader, Currency.class, "currency", "code");
        builder.addDirectMapping("code", String.class, "code");
        builder.addDirectMapping("name", String.class, "name");
        builder.addDirectMapping("symbol", String.class, "symbol");
        return build(builder, Currency.class, CacheIsolationType.SHARED);
    }
}
