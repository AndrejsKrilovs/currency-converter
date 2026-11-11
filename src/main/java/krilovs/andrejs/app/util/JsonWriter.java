package krilovs.andrejs.app.util;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.experimental.UtilityClass;
import org.infinispan.commons.dataconversion.MediaType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@UtilityClass
public class JsonWriter {
    public static final ObjectMapper JSON = new ObjectMapper()
            .enable(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN);

    public static void writeJson(HttpServletResponse resp, int status, Object body) throws IOException {
        resp.setStatus(status);
        resp.setContentType(MediaType.APPLICATION_JSON_TYPE);
        resp.setCharacterEncoding(StandardCharsets.UTF_8);
        JSON.writeValue(resp.getOutputStream(), body);
    }
}
