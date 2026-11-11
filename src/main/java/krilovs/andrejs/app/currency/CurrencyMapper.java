package krilovs.andrejs.app.currency;

import jakarta.persistence.EntityManager;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
public interface CurrencyMapper {
    CurrencyMapper INSTANCE = Mappers.getMapper(CurrencyMapper.class);

    CurrencyResponse toCurrencyResponse(Currency currency);
    Currency toCurrency(CurrencyRequest request);

    default Currency toCurrency(String code, @Context EntityManager em) {
        return em.find(Currency.class, code.toUpperCase());
    }
}
