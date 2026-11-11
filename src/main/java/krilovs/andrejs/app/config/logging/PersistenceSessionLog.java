package krilovs.andrejs.app.config.logging;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.persistence.logging.DefaultSessionLog;
import org.eclipse.persistence.logging.SessionLog;
import org.eclipse.persistence.logging.SessionLogEntry;

import java.util.Locale;
import java.util.stream.Stream;

/**
 * Default EclipseLink log ("[EL Fine]: sql: ...") that hides infrastructure SQL:
 * sequence fetches ({@code select nextval(...)}) and DDL. Business statements, including SELECTs, are logged.
 * Enabled through the {@code eclipselink.logging.logger} persistence unit property.
 */
@Slf4j
public class PersistenceSessionLog extends DefaultSessionLog {

    @Override
    public void log(SessionLogEntry entry) {
        var message = entry.getMessage();
        // the exception itself is logged as an entry with an empty message; it is already in the wide event
        if (message == null || message.isBlank()) {
            return;
        }
        if (SessionLog.SQL.equals(entry.getNameSpace()) && isInfrastructureSql(message)) {
            return;
        }
        log.warn(message);
    }

    private static boolean isInfrastructureSql(String sql) {
        var normalized = sql.strip().toLowerCase(Locale.ROOT);
        // "select 1" is EclipseLink's connection validation ping after a failed statement
        return Stream.of("select nextval", "select 1", "create", "drop", "alter").anyMatch(normalized::startsWith);
    }
}
