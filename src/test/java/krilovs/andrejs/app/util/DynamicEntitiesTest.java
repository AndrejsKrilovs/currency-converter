package krilovs.andrejs.app.util;

import org.eclipse.persistence.dynamic.DynamicClassLoader;
import org.junit.jupiter.api.Test;

import static krilovs.andrejs.app.util.DynamicEntities.CURRENCY;
import static krilovs.andrejs.app.util.DynamicEntities.RATE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DynamicEntitiesTest {
    private final DynamicEntities types = DynamicEntities.define(new DynamicClassLoader(getClass().getClassLoader()));

    @Test
    void shouldProvideTypesByName() {
        assertEquals("Currency", types.get(CURRENCY).getDescriptor().getAlias());
        assertEquals("Rate", types.get(RATE).getDescriptor().getAlias());
    }

    @Test
    void shouldProvideAllTypes() {
        assertEquals(2, types.all().length);
    }

    @Test
    void shouldNotProvideUnknownType() {
        var exception = assertThrows(IllegalArgumentException.class, () -> types.get("Unknown"));
        assertEquals("Unknown dynamic type: Unknown", exception.getMessage());
    }
}
