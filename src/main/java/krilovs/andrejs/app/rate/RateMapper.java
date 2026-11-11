package krilovs.andrejs.app.rate;

import krilovs.andrejs.app.currency.CurrencyMapper;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper(uses = CurrencyMapper.class)
public interface RateMapper {
    RateMapper INSTANCE = Mappers.getMapper(RateMapper.class);

    RateResponse toRateResponse(Rate rate);
}
