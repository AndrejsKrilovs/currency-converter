package krilovs.andrejs.app.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.postgresql.util.PSQLException;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/** Short, readable view of an EclipseLink/PostgreSQL failure instead of its multi-line message. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DatabaseError(String detail, String sql, List<String> bind, String cause) {
    private static final Pattern SQL = Pattern.compile("^Call:\\s+(.*)$", Pattern.MULTILINE);
    private static final Pattern TABLE = Pattern.compile("(is not present) in table \"[^\"]*\"");
    private static final Pattern BIND = Pattern.compile("^\\s*bind =>\\s+\\[(.*)]$", Pattern.MULTILINE);

    public static DatabaseError toDebug(Throwable error) {
        var message = String.valueOf(error.getMessage());
        var detail = detail(error);
        return new DatabaseError(detail, group(SQL, message), bind(message), detail == null ? rootMessage(error) : null);
    }

    /** Debug detail minus the internal table name: "Key (base_currency)=(RUR) is not present". */
    public static DatabaseError toClient(Throwable error) {
        var detail = detail(error);
        return new DatabaseError(detail == null ? null : TABLE.matcher(detail).replaceFirst("$1"), null, null, null);
    }

    /** PostgreSQL's own "Detail: Key (code)=(EUR) already exists." taken from the cause chain. */
    private static String detail(Throwable error) {
        for (var cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof PSQLException pg && pg.getServerErrorMessage() != null) {
                var detail = pg.getServerErrorMessage().getDetail();
                return detail == null ? null : detail.replaceFirst("\\.$", "");
            }
        }
        return null;
    }

    /** Message of the innermost cause, e.g. PostgreSQL's "ERROR: syntax error at or near ...", for failures without a Detail. */
    private static String rootMessage(Throwable error) {
        var root = error;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getMessage();
    }

    private static List<String> bind(String message) {
        var bind = group(BIND, message);
        return bind == null ? List.of() : Arrays.asList(bind.split(",\\s+"));
    }

    private static String group(Pattern pattern, String text) {
        var matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }
}
