package krilovs.andrejs.app.currency;

import krilovs.andrejs.app.util.AbstractTestClass;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CurrencyMapperTest extends AbstractTestClass {
    @Test
    void shouldMapEntityToResponse() {
        var response = CurrencyMapper.toResponse(currencyEntity("EUR", "Euro", "€"));

        assertEquals(currency("EUR", "Euro", "€"), response);
    }

    @Test
    void shouldMapRequestToEntity() {
        var entity = CurrencyMapper.toEntity(new CurrencyRequest("EUR", "Euro", "€"), persistence);

        assertEquals("EUR", entity.get("code"));
        assertEquals("Euro", entity.get("name"));
        assertEquals("€", entity.get("symbol"));
    }
}
