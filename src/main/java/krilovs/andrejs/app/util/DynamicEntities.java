package krilovs.andrejs.app.util;

import krilovs.andrejs.app.currency.Currency;
import krilovs.andrejs.app.rate.Rate;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.eclipse.persistence.annotations.CacheIsolationType;
import org.eclipse.persistence.dynamic.DynamicClassLoader;
import org.eclipse.persistence.dynamic.DynamicType;
import org.eclipse.persistence.jpa.dynamic.JPADynamicTypeBuilder;

/**
 * EclipseLink dynamic entities: tables are described in code, there are no @Entity classes.
 * Each type is defined in the package of its entity ({@link Currency}, {@link Rate});
 * the names are the JPQL aliases, e.g. {@code SELECT r FROM Rate r}.
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class DynamicEntities {
    public static final String CURRENCY = "Currency";
    public static final String RATE = "Rate";

    private final DynamicType currency;
    private final DynamicType rate;

    public static DynamicEntities define(DynamicClassLoader loader) {
        var currency = Currency.define(loader);
        return new DynamicEntities(currency, Rate.define(loader, currency));
    }

    public DynamicType get(String name) {
        return switch (name) {
            case CURRENCY -> currency;
            case RATE -> rate;
            default -> throw new IllegalArgumentException("Unknown dynamic type: " + name);
        };
    }

    public DynamicType[] all() {
        return new DynamicType[]{currency, rate};
    }

    /**
     * Builder of the table type of {@code owner}. The generated class sits next to it as {@code <owner>Entity}:
     * a class named like the owner itself would be taken from the parent class loader instead of being generated.
     */
    public static JPADynamicTypeBuilder builder(DynamicClassLoader loader, Class<?> owner, String table, String... primaryKey) {
        var builder = new JPADynamicTypeBuilder(loader.createDynamicClass(owner.getName() + "Entity"), null, table);
        builder.setPrimaryKeyFields(primaryKey);
        return builder;
    }

    /** The JPQL alias is the name of the owner, e.g. {@code Rate}. */
    public static DynamicType build(JPADynamicTypeBuilder builder, Class<?> owner, CacheIsolationType isolation) {
        var type = builder.getType();
        type.getDescriptor().setAlias(owner.getSimpleName());
        type.getDescriptor().setCacheIsolation(isolation);
        return type;
    }
}
