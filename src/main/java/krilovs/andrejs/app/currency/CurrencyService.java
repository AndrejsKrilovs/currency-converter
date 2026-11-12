package krilovs.andrejs.app.currency;

import krilovs.andrejs.app.config.PersistenceConfig;
import lombok.RequiredArgsConstructor;
import org.eclipse.persistence.dynamic.DynamicEntity;

import java.util.List;
import java.util.Optional;

import static krilovs.andrejs.app.util.DynamicEntities.CURRENCY;

@RequiredArgsConstructor
public class CurrencyService {
    private final PersistenceConfig persistence;

    public List<CurrencyResponse> findAll() {
        return persistence.withEntityManager(em ->
                em.createQuery("SELECT c FROM Currency c ORDER BY c.code", DynamicEntity.class).getResultList().stream()
                        .map(CurrencyMapper::toResponse)
                        .toList()
        );
    }

    public Optional<CurrencyResponse> findById(String code) {
        return Optional.ofNullable(persistence.<DynamicEntity>withEntityManager(em ->
                        em.find(persistence.entityClass(CURRENCY), code)))
                .map(CurrencyMapper::toResponse);
    }

    public CurrencyResponse persist(CurrencyRequest request) {
        return persistence.inTransaction(em -> {
            var currency = CurrencyMapper.toEntity(request, persistence);
            em.persist(currency);
            em.flush();
            return CurrencyMapper.toResponse(currency);
        });
    }
}
