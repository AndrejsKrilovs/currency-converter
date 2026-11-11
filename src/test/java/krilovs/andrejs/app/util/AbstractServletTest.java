package krilovs.andrejs.app.util;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.net.http.HttpResponse;

import static krilovs.andrejs.app.util.HttpTestServer.json;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Starts the servlet under test on a real Jetty before each test; the services behind it are mocks. */
public abstract class AbstractServletTest extends AbstractTestClass {
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

    /** 400 with the first message of {"errors": {field: [...]}} produced by Bean Validation. */
    protected static void assertValidationError(HttpResponse<String> response, String field, String message) {
        assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.statusCode());
        assertEquals(message, json(response).get("errors").get(field).get(0).asText());
    }

    protected void assertIncorrectEndpoint(String method, String body) {
        assertError(server.send(method, "/other", body), HttpServletResponse.SC_NOT_FOUND, "Incorrect endpoint");
    }
}
