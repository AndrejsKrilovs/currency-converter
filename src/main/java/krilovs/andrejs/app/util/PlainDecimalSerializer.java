package krilovs.andrejs.app.util;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import java.io.IOException;
import java.math.BigDecimal;

/** Writes a BigDecimal as a JSON number without trailing zeros and without exponent: 20281.150000 -> 20281.15, 20.00 -> 20. */
public class PlainDecimalSerializer extends StdSerializer<BigDecimal> {
    public PlainDecimalSerializer() {
        super(BigDecimal.class);
    }

    @Override
    public void serialize(BigDecimal value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        gen.writeNumber(value.stripTrailingZeros());
    }
}
