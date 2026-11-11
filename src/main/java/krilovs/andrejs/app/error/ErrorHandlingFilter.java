package krilovs.andrejs.app.error;

import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import jakarta.persistence.PersistenceException;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolationException;
import krilovs.andrejs.app.config.logging.WideEvent;

import java.io.IOException;
import java.util.Map;

import static krilovs.andrejs.app.util.JsonWriter.writeJson;

/** Maps domain exceptions thrown by servlets to HTTP error responses. */
public class ErrorHandlingFilter implements Filter {
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        try {
            chain.doFilter(request, response);
        }
        catch (NoEntityException e) {
            var item = Map.of("message", e.getMessage(), "value", e.getValue());
            WideEvent.put("error", item);
            writeJson((HttpServletResponse) response, HttpServletResponse.SC_NOT_FOUND, item);
        }
        catch (IncorrectPathException e) {
            var item = Map.of("error", e.getMessage());
            WideEvent.put("error", e.getMessage());
            writeJson((HttpServletResponse) response, HttpServletResponse.SC_BAD_REQUEST, item);
        }
        catch (MismatchedInputException e) {
            var msg = "Incorrect json provided";
            WideEvent.put("error", msg);
            writeJson((HttpServletResponse) response, HttpServletResponse.SC_NOT_ACCEPTABLE, Map.of("error", msg));
        }
        catch (ConstraintViolationException e) {
            WideEvent.put("error", ViolationError.of(e));
            writeJson((HttpServletResponse) response, HttpServletResponse.SC_BAD_REQUEST, ViolationError.of(e));
        }
        catch (PersistenceException e) {
            WideEvent.put("error", DatabaseError.toDebug(e));
            writeJson((HttpServletResponse) response, HttpServletResponse.SC_CONFLICT, DatabaseError.toClient(e));
        }
    }
}
