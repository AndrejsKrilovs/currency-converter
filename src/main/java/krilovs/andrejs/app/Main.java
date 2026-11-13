import jakarta.servlet.DispatcherType;
import krilovs.andrejs.app.config.AppConfigLoader;
import krilovs.andrejs.app.config.logging.WideEventFilter;
import krilovs.andrejs.app.config.jpa.PersistenceConfig;
import krilovs.andrejs.app.currency.CurrencyService;
import krilovs.andrejs.app.currency.CurrencyServlet;
import krilovs.andrejs.app.error.ErrorHandlingFilter;
import krilovs.andrejs.app.exchange.ExchangeServlet;
import krilovs.andrejs.app.exchange.ExchangeService;
import krilovs.andrejs.app.rate.RateService;
import krilovs.andrejs.app.rate.RateServlet;
import org.eclipse.jetty.ee11.servlet.FilterHolder;
import org.eclipse.jetty.ee11.servlet.ServletContextHandler;
import org.eclipse.jetty.ee11.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import org.slf4j.LoggerFactory;

import java.util.EnumSet;

void main() throws Exception {
    try (var persistence = new PersistenceConfig()) {
        var context = new ServletContextHandler();
        context.setContextPath("/api/v1");
        context.addFilter(new FilterHolder(new WideEventFilter()), "/*", EnumSet.of(DispatcherType.REQUEST));
        context.addFilter(new FilterHolder(new ErrorHandlingFilter()), "/*", EnumSet.of(DispatcherType.REQUEST));

        var currencyService = new CurrencyService(persistence);
        // Loads all currencies into the EclipseLink shared cache (and initialises the persistence unit)
        // so that rate lookups by key do not hit the database
        var log = LoggerFactory.getLogger("krilovs.andrejs.app");
        log.info("Currency cache warmed up: {} currencies", currencyService.findAll().size());

        var currencyHolder = new ServletHolder(new CurrencyServlet(currencyService));
        context.addServlet(currencyHolder, "/currencies");
        context.addServlet(currencyHolder, "/currency/*");

        var rateService = new RateService(persistence);
        var rateHolder = new ServletHolder(new RateServlet(rateService));
        context.addServlet(rateHolder, "/exchangeRates");
        context.addServlet(rateHolder, "/exchangeRate/*");

        var exchangeService = new ExchangeService(rateService);
        var exchangeHolder = new ServletHolder(new ExchangeServlet(exchangeService));
        context.addServlet(exchangeHolder, "/exchange");

        var port = AppConfigLoader.load().server().port();
        var server = new Server(port);
        server.setHandler(context);

        try {
            server.start();
            log.info("Application started on port {}, context path /api/v1", port);
            server.join();
        }
        finally {
            server.stop();
        }
    }
}