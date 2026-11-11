package krilovs.andrejs.app.currency;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import krilovs.andrejs.app.error.NoEntityException;
import krilovs.andrejs.app.util.AbstractServlet;
import lombok.RequiredArgsConstructor;

import java.io.IOException;

@RequiredArgsConstructor
public class CurrencyServlet extends AbstractServlet {
    private final CurrencyService currencyService;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        switch (req.getServletPath()) {
            case "/currencies" -> respondList(resp, "currencies", currencyService.findAll());
            case "/currency" -> {
                var code = pathValue(req, "Missing currency code");
                var currency = currencyService.findById(code)
                        .orElseThrow(() -> new NoEntityException("Currency code not found", code));
                respond(resp, HttpServletResponse.SC_OK, currency);
            }
            default -> handleNotFound(resp);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if ("/currencies".equals(req.getServletPath())) {
            var currency = currencyService.persist(body(req, Currency.class));
            respond(resp, HttpServletResponse.SC_CREATED, currency);
            return;
        }
        handleNotFound(resp);
    }
}
