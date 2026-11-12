package krilovs.andrejs.app.rate;

import krilovs.andrejs.app.currency.Currency;
import org.eclipse.persistence.annotations.CacheIsolationType;
import org.eclipse.persistence.dynamic.DynamicClassLoader;
import org.eclipse.persistence.dynamic.DynamicType;
import org.eclipse.persistence.mappings.OneToOneMapping;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateTest {
    private DynamicType currency;
    private DynamicType rate;

    @BeforeEach
    void defineTypes() {
        var loader = new DynamicClassLoader(getClass().getClassLoader());
        currency = Currency.define(loader);
        rate = Rate.define(loader, currency);
    }

    @Test
    void shouldDefineRateType() {
        var descriptor = rate.getDescriptor();

        assertEquals("krilovs.andrejs.app.rate.RateEntity", rate.getJavaClass().getName());
        assertEquals("Rate", descriptor.getAlias());
        assertEquals("rate", descriptor.getTableName());
        assertEquals(List.of("base_currency", "target_currency"), descriptor.getPrimaryKeyFieldNames());
        assertEquals(
                Set.of("baseCurrencyCode", "targetCurrencyCode", "rate", "baseCurrency", "targetCurrency"),
                Set.copyOf(rate.getPropertiesNames())
        );
        assertEquals(CacheIsolationType.ISOLATED, descriptor.getCacheIsolation());
    }

    @ParameterizedTest
    @ValueSource(strings = {"baseCurrency", "targetCurrency"})
    void shouldReferenceCurrencyReadOnlyWithoutIndirection(String attribute) {
        var relation = (OneToOneMapping) rate.getDescriptor().getMappingForAttributeName(attribute);

        assertEquals(currency.getJavaClass(), relation.getReferenceClass());
        assertTrue(relation.isReadOnly());
        assertFalse(relation.usesIndirection());
    }
}
