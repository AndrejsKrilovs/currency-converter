package krilovs.andrejs.app.currency;

import org.eclipse.persistence.annotations.CacheIsolationType;
import org.eclipse.persistence.dynamic.DynamicClassLoader;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CurrencyTest {
    @Test
    void shouldDefineCurrencyType() {
        var type = Currency.define(new DynamicClassLoader(getClass().getClassLoader()));
        var descriptor = type.getDescriptor();

        assertEquals("krilovs.andrejs.app.currency.CurrencyEntity", type.getJavaClass().getName());
        assertEquals("Currency", descriptor.getAlias());
        assertEquals("currency", descriptor.getTableName());
        assertEquals(List.of("code"), descriptor.getPrimaryKeyFieldNames());
        assertEquals(Set.of("code", "name", "symbol"), Set.copyOf(type.getPropertiesNames()));
        assertEquals(CacheIsolationType.SHARED, descriptor.getCacheIsolation());
    }
}
