package krilovs.andrejs.app.util;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletResponse;
import krilovs.andrejs.app.config.jpa.PersistenceConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.math.BigDecimal;
import java.net.http.HttpResponse;

import static krilovs.andrejs.app.util.HttpTestServer.json;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Starts the servlet under test on a real Jetty before each test. The services behind it are real too,
 * working with the shared PostgreSQL of {@link TestDatabase} (seeded by Liquibase). Tests that write
 * use their own currency codes and pairs, because the data is shared and never cleaned up.
 */
public abstract class AbstractServletTest {
    protected static final PersistenceConfig PERSISTENCE = TestDatabase.persistence();

    protected HttpTestServer server;

    protected abstract HttpServlet servlet();

    protected abstract String[] paths();

    @BeforeEach
    void startServer() {
        server = new HttpTestServer(servlet(), paths());
    }

    @AfterEach
    void stopServer() {
        server.close();
    }

    /** Status plus the {"error": "..."} body written by the error filter. */
    protected static void assertError(HttpResponse<String> response, int status, String message) {
        assertEquals(status, response.statusCode());
        assertEquals(message, json(response).get("error").asText());
    }

    /** 404 with {"message": "...", "value": ...} produced for a missing entity. */
    protected static JsonNode assertNotFound(HttpResponse<String> response, String message) {
        assertEquals(HttpServletResponse.SC_NOT_FOUND, response.statusCode());
        assertEquals(message, json(response).get("message").asText());
        return json(response).get("value");
    }

    /** 400 with the first message of {"errors": {field: [...]}} produced by Bean Validation. */
    protected static void assertValidationError(HttpResponse<String> response, String field, String message) {
        assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.statusCode());
        assertEquals(message, json(response).get("errors").get(field).get(0).asText());
    }

    /** Compares by value: the database returns numeric(19,6), so "1.1224" comes back as "1.122400". */
    protected static void assertDecimal(String expected, JsonNode actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(new BigDecimal(actual.asText())),
                "expected " + expected + " but was " + actual.asText());
    }

    protected void assertIncorrectEndpoint(String method, String body) {
        assertError(server.send(method, "/other", body), HttpServletResponse.SC_NOT_FOUND, "Incorrect endpoint");
    }
}
