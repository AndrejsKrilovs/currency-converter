package krilovs.andrejs.app.config;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.io.IOException;
import java.io.UncheckedIOException;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AppConfigLoader {
    private static final String DEFAULT_RESOURCE = "application.yaml";
    private static final YAMLMapper MAPPER = YAMLMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .build();

    public static AppConfig load() {
        return load(DEFAULT_RESOURCE);
    }

    public static AppConfig load(String resource) {
        try (var in = AppConfigLoader.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Config not found on classpath: " + resource);
            }
            return MAPPER.readValue(in, AppConfig.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + resource, e);
        }
    }
}
