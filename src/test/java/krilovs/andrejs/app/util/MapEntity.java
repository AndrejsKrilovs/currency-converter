package krilovs.andrejs.app.util;

import org.eclipse.persistence.dynamic.DynamicEntity;

import java.util.HashMap;
import java.util.Map;

/** In-memory stand-in for an EclipseLink dynamic entity, so tests need neither a database nor a session. */
public class MapEntity implements DynamicEntity {
    private final Map<String, Object> values = new HashMap<>();

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(String propertyName) {
        return (T) values.get(propertyName);
    }

    @Override
    public DynamicEntity set(String propertyName, Object value) {
        values.put(propertyName, value);
        return this;
    }

    @Override
    public boolean isSet(String propertyName) {
        return values.containsKey(propertyName);
    }
}
