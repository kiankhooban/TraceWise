package tracewise.ui.gui;

import java.util.Locale;
import java.util.Map;

/** A command-bar entry (Ctrl+K): a short code or an id that names where to go. It never changes data itself. */
record Command(Target target, String argument) {

    enum Target { ALERTS, CASES, APPROVALS, ADMIN, ASK, UNKNOWN }

    private static final Map<String, Target> CODES = Map.of("ALRT", Target.ALERTS, "CASE", Target.CASES,
            "APRV", Target.APPROVALS, "ADMN", Target.ADMIN, "ASK", Target.ASK);

    private static final Map<String, Target> ID_PREFIXES = Map.of("ALR-", Target.ALERTS, "CAS-", Target.CASES,
            "APR-", Target.APPROVALS);

    /** null for blank input. */
    static Command parse(String input) {
        String text = input == null ? "" : input.strip();
        if (text.isEmpty()) {
            return null;
        }
        String[] parts = text.split("\\s+", 2);
        String head = parts[0].toUpperCase(Locale.ROOT);
        String rest = parts.length > 1 ? parts[1].strip() : "";
        Target code = CODES.get(head);
        if (code == Target.ASK) {
            return new Command(code, rest);
        }
        if (code != null) {
            return new Command(code, rest.toUpperCase(Locale.ROOT));
        }
        for (var prefix : ID_PREFIXES.entrySet()) {
            if (head.startsWith(prefix.getKey())) {
                return new Command(prefix.getValue(), head);
            }
        }
        return new Command(Target.UNKNOWN, text);
    }
}
