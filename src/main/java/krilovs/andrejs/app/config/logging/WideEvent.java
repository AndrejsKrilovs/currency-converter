package krilovs.andrejs.app.config.logging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectWriter;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

import java.util.LinkedHashMap;
import java.util.Map;

import static krilovs.andrejs.app.util.JsonWriter.JSON;

/**
 * One structured "wide" event per request: code anywhere in the request thread adds context with
 * {@link #put}, and {@link WideEventFilter} emits everything as a single JSON log line at the end.
 */
public final class WideEvent {
    private static final Logger LOG = LoggerFactory.getLogger(WideEvent.class);
    private static final Logger FILE_LOG = LoggerFactory.getLogger("wide-event.file");
    private static final ThreadLocal<WideEvent> CURRENT = new ThreadLocal<>();

    private final Map<String, Object> fields = new LinkedHashMap<>();
    private final Map<String, Object> data = new LinkedHashMap<>();

    private WideEvent() {}

    /** Adds a field to the current request's event; no-op outside of a request. */
    public static void put(String key, Object value) {
        var event = CURRENT.get();
        if (event != null) {
            event.fields.put(key, value);
        }
    }

    static void begin() {
        CURRENT.set(new WideEvent());
    }

    public static void emit(int status, boolean failed) {
        var event = CURRENT.get();
        CURRENT.remove();
        if (event == null) {
            return;
        }

        event.fields.put("status", status);
        if (!event.data.isEmpty()) {
            event.fields.put("data", event.data);
        }
        Level level;
        if (failed || status >= HttpServletResponse.SC_INTERNAL_SERVER_ERROR) {
            level = Level.ERROR;
        }
        else if (status >= HttpServletResponse.SC_BAD_REQUEST) {
            level = Level.WARN;
        }
        else {
            level = Level.INFO;
        }

        // console: one compact line; file: pretty-printed JSON (see logback.xml)
        LOG.atLevel(level).log(event.toJson(JSON.writer()));
        FILE_LOG.atLevel(level).log(event.toJson(JSON.writerWithDefaultPrettyPrinter()));
    }

    private String toJson(ObjectWriter writer) {
        try {
            return writer.writeValueAsString(fields);
        }
        catch (JsonProcessingException e) {
            return fields.toString();
        }
    }
}
