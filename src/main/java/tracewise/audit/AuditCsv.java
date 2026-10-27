package tracewise.audit;

import java.io.IOException;
import java.io.Writer;
import java.util.List;

/** F08: audit entries as CSV (RFC 4180 quoting, CRLF line ends), safe to open in a spreadsheet. */
public final class AuditCsv {

    private AuditCsv() {
    }

    public static void write(List<AuditEntry> entries, Writer out) throws IOException {
        out.write("timestamp,actor,actor_type,event_type,target,details,outcome\r\n");
        for (AuditEntry e : entries) {
            out.write(String.join(",", e.timestamp().toString(), field(e.actor().name()), e.actor().type().name(),
                    field(e.eventType()), field(e.target()), field(e.details()), field(e.outcome())) + "\r\n");
        }
        out.flush();
    }

    private static String field(String value) {
        if (value == null) {
            return "";
        }
        // details can hold text from imported data: a leading = + - @ (or tab or CR, OWASP) can run as a formula
        String safe = !value.isEmpty() && "=+-@\t\r".indexOf(value.charAt(0)) >= 0 ? "'" + value : value;
        return safe.contains(",") || safe.contains("\"") || safe.contains("\n") || safe.contains("\r")
                ? "\"" + safe.replace("\"", "\"\"") + "\"" : safe;
    }
}
