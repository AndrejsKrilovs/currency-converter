package krilovs.andrejs.app.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import lombok.experimental.UtilityClass;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

/**
 * Loads application.yaml. String values may contain {@code ${ENV_VAR}} or {@code ${ENV_VAR:default}}
 * placeholders, resolved against the environment after the YAML is parsed, so values with special
 * characters (passwords) cannot break the YAML syntax.
 */
@UtilityClass
public final class AppConfigLoader {
    private static final String DEFAULT_RESOURCE = "application.yaml";
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([A-Za-z0-9_]+)(?::([^}]*))?}");
    private static final YAMLMapper MAPPER = YAMLMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .build();

    public static AppConfig load() {
        return load(DEFAULT_RESOURCE);
    }

    public static AppConfig load(String resource) {
        return load(resource, System::getenv);
    }

    static AppConfig load(String resource, UnaryOperator<String> env) {
        try (var in = AppConfigLoader.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Config not found on classpath: " + resource);
            }
            return MAPPER.treeToValue(resolve(MAPPER.readTree(in), env), AppConfig.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + resource, e);
        }
    }

    static JsonNode resolve(JsonNode node, UnaryOperator<String> env) {
        if (node instanceof ObjectNode object) {
            object.properties().forEach(entry -> entry.setValue(resolve(entry.getValue(), env)));
        }
        else if (node instanceof ArrayNode array) {
            for (int i = 0; i < array.size(); i++) {
                array.set(i, resolve(array.get(i), env));
            }
        }
        else if (node instanceof TextNode text) {
            return TextNode.valueOf(substitute(text.textValue(), env));
        }
        return node;
    }

    private static String substitute(String value, UnaryOperator<String> env) {
        return PLACEHOLDER.matcher(value).replaceAll(match -> {
            var fromEnv = env.apply(match.group(1));
            if (fromEnv != null) {
                return java.util.regex.Matcher.quoteReplacement(fromEnv);
            }
            if (match.group(2) != null) {
                return java.util.regex.Matcher.quoteReplacement(match.group(2));
            }
            throw new IllegalStateException("Environment variable %s is not set and has no default".formatted(match.group(1)));
        });
    }
}
