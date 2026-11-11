package krilovs.andrejs.app.util;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServlet;
import krilovs.andrejs.app.error.ErrorHandlingFilter;
import org.eclipse.jetty.ee11.servlet.FilterHolder;
import org.eclipse.jetty.ee11.servlet.ServletContextHandler;
import org.eclipse.jetty.ee11.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.EnumSet;

import static krilovs.andrejs.app.util.JsonWriter.JSON;

/**
 * Real Jetty on a random port with the same context path and error filter as Main, serving one servlet
 * over real HTTP. Services behind the servlet are mocks, so no database is needed.
 */
public final class HttpTestServer implements AutoCloseable {
    private static final String CONTEXT_PATH = "/api/v1";

    private final Server server = new Server(0);
    private final HttpClient client = HttpClient.newHttpClient();

    public HttpTestServer(HttpServlet servlet, String... paths) {
        var context = new ServletContextHandler();
        context.setContextPath(CONTEXT_PATH);
        context.addFilter(new FilterHolder(new ErrorHandlingFilter()), "/*", EnumSet.of(DispatcherType.REQUEST));

        var holder = new ServletHolder(servlet);
        for (var path : paths) {
            context.addServlet(holder, path);
        }
        server.setHandler(context);
        try {
            server.start();
        }
        catch (Exception e) {
            throw new IllegalStateException("Cannot start the test server", e);
        }
    }

    public HttpResponse<String> get(String path) {
        return send("GET", path, null);
    }

    public HttpResponse<String> post(String path, String json) {
        return send("POST", path, json);
    }

    public HttpResponse<String> patch(String path, String json) {
        return send("PATCH", path, json);
    }

    public static JsonNode json(HttpResponse<String> response) {
        try {
            return JSON.readTree(response.body());
        }
        catch (IOException e) {
            throw new IllegalStateException("Response is not JSON: " + response.body(), e);
        }
    }

    public HttpResponse<String> send(String method, String path, String json) {
        var port = ((ServerConnector) server.getConnectors()[0]).getLocalPort();
        var publisher = json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json);
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + CONTEXT_PATH + path))
                .header("Content-Type", "application/json")
                .method(method, publisher)
                .build();
        try {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
        catch (IOException | InterruptedException e) {
            throw new IllegalStateException("Request failed: " + method + " " + path, e);
        }
    }

    @Override
    public void close() {
        try {
            server.stop();
        }
        catch (Exception e) {
            throw new IllegalStateException("Cannot stop the test server", e);
        }
    }
}
