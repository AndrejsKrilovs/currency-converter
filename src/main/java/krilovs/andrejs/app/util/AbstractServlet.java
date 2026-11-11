package krilovs.andrejs.app.util;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import krilovs.andrejs.app.config.logging.WideEvent;
import krilovs.andrejs.app.error.IncorrectPathException;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static krilovs.andrejs.app.util.BeanValidator.validate;
import static krilovs.andrejs.app.util.JsonWriter.JSON;
import static krilovs.andrejs.app.util.JsonWriter.writeJson;

public abstract class AbstractServlet extends HttpServlet {
    protected static String pathValue(HttpServletRequest req, String missingMessage) {
        var pathInfo = req.getPathInfo();
        var value = (pathInfo == null) ? "" : pathInfo.replaceFirst("^/", "").toUpperCase();
        if (value.isBlank()) {
            throw new IncorrectPathException(missingMessage);
        }
        return value;
    }

    protected static String param(HttpServletRequest req, String name) {
        var value = req.getParameter(name);
        if (value == null || value.isBlank()) {
            throw new IncorrectPathException("Missing query parameter: " + name);
        }
        return value.trim();
    }

    protected static BigDecimal decimalParam(HttpServletRequest req, String name) {
        try {
            return new BigDecimal(param(req, name));
        }
        catch (NumberFormatException e) {
            throw new IncorrectPathException("Amount must be a number");
        }
    }

    /** Request body parsed from JSON into the type and validated, otherwise 400 / 406. */
    protected static <T> T body(HttpServletRequest req, Class<T> type) throws IOException {
        return validate(JSON.readValue(req.getInputStream(), type));
    }

    /** Writes the list as JSON and records only its size in the wide event, e.g. "rates.count". */
    protected static void respondList(HttpServletResponse resp, String name, List<?> items) throws IOException {
        writeJson(resp, HttpServletResponse.SC_OK, items);
        WideEvent.put(name + ".count", items.size());
    }

    protected static void respond(HttpServletResponse resp, int status, Object data) throws IOException {
        writeJson(resp, status, data);
        WideEvent.put("data", data);
    }

    protected static void handleNotFound(HttpServletResponse resp) throws IOException {
        writeJson(resp, HttpServletResponse.SC_NOT_FOUND, Map.of("error", "Incorrect endpoint"));
        WideEvent.put("error", "Incorrect endpoint");
    }
}
