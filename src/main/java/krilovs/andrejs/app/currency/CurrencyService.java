package krilovs.andrejs.app.currency;

import krilovs.andrejs.app.config.PersistenceConfig;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
public class CurrencyService {
    private final PersistenceConfig persistence;

    public List<Currency> findAll() {
        return persistence.withEntityManager(em ->
                em.createQuery("SELECT c FROM Currency c ORDER BY c.code", Currency.class).getResultList()
        );
    }

    public Optional<Currency> findById(String code) {
        return Optional.ofNullable(persistence.withEntityManager(em -> em.find(Currency.class, code)));
    }

    public Currency persist(Currency currency) {
        return persistence.inTransaction(em -> {
            em.persist(currency);
            em.flush();
            return currency;
        });
    }
}
