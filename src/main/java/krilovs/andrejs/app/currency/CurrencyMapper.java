package krilovs.andrejs.app.currency;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
public interface CurrencyMapper {
    CurrencyMapper INSTANCE = Mappers.getMapper(CurrencyMapper.class);

    CurrencyResponse toCurrencyResponse(Currency currency);
    Currency toCurrency(CurrencyRequest request);
}
