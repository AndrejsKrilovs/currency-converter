package krilovs.andrejs.app.config.logging;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.eclipse.jetty.ee11.servlet.ServletContextRequest;

import java.io.IOException;
import java.time.OffsetDateTime;

/** Builds and emits one {@link WideEvent} per HTTP request. */
public class WideEventFilter implements Filter {
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        var req = (HttpServletRequest) request;
        var resp = (HttpServletResponse) response;
        var start = System.nanoTime();

        var failed = false;
        try {
            WideEvent.begin();
            var beginNanos = ServletContextRequest.getServletContextRequest(req).getBeginNanoTime();
            WideEvent.put("time", OffsetDateTime.now().minusNanos(System.nanoTime() - beginNanos).toString());
            WideEvent.put("method", req.getMethod());
            WideEvent.put("path", req.getRequestURI());
            chain.doFilter(request, response);
        }
        catch (IOException | ServletException | RuntimeException e) {
            failed = true;
            WideEvent.put("error", e.getClass().getName() + ": " + e.getMessage());
            throw e;
        }
        finally {
            WideEvent.put("duration_ms", (System.nanoTime() - start) / 1_000_000);
            WideEvent.emit(failed ? HttpServletResponse.SC_INTERNAL_SERVER_ERROR : resp.getStatus(), failed);
        }
    }
}
