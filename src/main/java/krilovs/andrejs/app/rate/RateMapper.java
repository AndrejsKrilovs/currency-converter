package krilovs.andrejs.app.rate;

import jakarta.persistence.EntityManager;
import krilovs.andrejs.app.currency.CurrencyMapper;
import krilovs.andrejs.app.error.NoEntityException;
import org.mapstruct.AfterMapping;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.mapstruct.factory.Mappers;

@Mapper(uses = CurrencyMapper.class)
public interface RateMapper {
    RateMapper INSTANCE = Mappers.getMapper(RateMapper.class);

    RateResponse toRateResponse(Rate rate);

    @Mapping(target = "id", source = "request")
    @Mapping(target = "baseCurrency", source = "baseCurrencyCode")
    @Mapping(target = "targetCurrency", source = "targetCurrencyCode")
    Rate toEntity(RateRequest request, @Context EntityManager em);

    @Mapping(target = "baseCurrency", source = "baseCurrencyCode", qualifiedByName = "upperCase")
    @Mapping(target = "targetCurrency", source = "targetCurrencyCode", qualifiedByName = "upperCase")
    RateId toRateId(RateRequest request);

    @Named("upperCase")
    default String upperCase(String code) {
        return code.toUpperCase();
    }

    @AfterMapping
    default void requireCurrencies(RateRequest request, @MappingTarget Rate rate) {
        if (rate.getBaseCurrency() == null || rate.getTargetCurrency() == null) {
            throw new NoEntityException("Currency not found for rate pair", request);
        }
    }
}
