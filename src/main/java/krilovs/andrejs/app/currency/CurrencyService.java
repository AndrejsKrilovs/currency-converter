package krilovs.andrejs.app.currency;

import krilovs.andrejs.app.config.PersistenceConfig;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
public class CurrencyService {
    private final static CurrencyMapper MAPPER = CurrencyMapper.INSTANCE;
    private final PersistenceConfig persistence;

    public List<CurrencyResponse> findAll() {
        return persistence.withEntityManager(em ->
                em.createQuery("SELECT c FROM Currency c ORDER BY c.code", Currency.class).getResultList().stream()
                        .map(MAPPER::toCurrencyResponse)
                        .toList()
        );
    }

    public Optional<CurrencyResponse> findById(String code) {
        return Optional.ofNullable(persistence.withEntityManager(em -> em.find(Currency.class, code)))
                .map(MAPPER::toCurrencyResponse);
    }

    public CurrencyResponse persist(CurrencyRequest request) {
        return persistence.inTransaction(em -> {
            var currency = MAPPER.toCurrency(request);
            em.persist(currency);
            em.flush();
            return MAPPER.toCurrencyResponse(currency);
        });
    }
}
