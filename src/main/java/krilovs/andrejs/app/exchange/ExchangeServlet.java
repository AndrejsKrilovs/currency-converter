package krilovs.andrejs.app.exchange;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import krilovs.andrejs.app.util.AbstractServlet;
import lombok.RequiredArgsConstructor;

import java.io.IOException;

import static krilovs.andrejs.app.util.BeanValidator.validate;

@RequiredArgsConstructor
public class ExchangeServlet extends AbstractServlet {
    private final ExchangeService exchangeService;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if ("/exchange".equals(req.getServletPath())) {
            var request = validate(new ExchangeRequest(
                    param(req, "from").toUpperCase(),
                    param(req, "to").toUpperCase(),
                    decimalParam(req, "amount")
            ));
            respond(resp, HttpServletResponse.SC_OK, exchangeService.convert(request));
            return;
        }
        handleNotFound(resp);
    }
}
