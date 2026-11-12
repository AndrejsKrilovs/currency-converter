package krilovs.andrejs.app.rate;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import krilovs.andrejs.app.error.IncorrectPathException;
import krilovs.andrejs.app.error.NoEntityException;
import krilovs.andrejs.app.util.AbstractServlet;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;

import static krilovs.andrejs.app.util.BeanValidator.validate;

@RequiredArgsConstructor
public class RateServlet extends AbstractServlet {
    private static final Pattern PAIR_PATTERN = Pattern.compile("^[A-Z]{6}$");
    private final RateService rateService;

    private record CurrencyPair(String base, String target) {}

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        switch (req.getServletPath()) {
            case "/exchangeRates" -> respondList(resp, "rates", rateService.findAll());
            case "/exchangeRate" -> {
                var pair = extractPair(req);
                respond(resp, HttpServletResponse.SC_OK, rateService.findByPair(pair.base(), pair.target())
                        .orElseThrow(() -> new NoEntityException("Exchange pair not found", List.of(pair.base(), pair.target()))));
            }
            default -> handleNotFound(resp);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if ("/exchangeRates".equals(req.getServletPath())) {
            respond(resp, HttpServletResponse.SC_CREATED, rateService.persist(body(req, RateRequest.class)));
            return;
        }
        handleNotFound(resp);
    }

    @Override
    protected void doPatch(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if ("/exchangeRate".equals(req.getServletPath())) {
            var pair = extractPair(req);
            var request = validate(new RateRequest(pair.base(), pair.target(), body(req, UpdateRateRequest.class).rate()));
            respond(resp, HttpServletResponse.SC_ACCEPTED, rateService.update(request));
            return;
        }
        handleNotFound(resp);
    }

    private CurrencyPair extractPair(HttpServletRequest req) {
        var pair = pathValue(req, "Missing currency pair");
        if (!PAIR_PATTERN.matcher(pair).matches()) {
            throw new IncorrectPathException("Incorrect currency pair, e.g. USDEUR");
        }
        return new CurrencyPair(pair.substring(0, 3), pair.substring(3));
    }
}
