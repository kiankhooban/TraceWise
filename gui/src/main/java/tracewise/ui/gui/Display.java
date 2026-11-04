package tracewise.ui.gui;

import java.time.Duration;

/** How raw values read on screen (docs/stage2/ui-design.md): every state is a word, never a code or a colour alone. */
final class Display {

    private Display() {
    }

    /** The rule names of the Stage 1 report (F02 rule table). */
    private static final java.util.Map<String, String> RULES = java.util.Map.of("R1", "Large cash",
            "R2", "Large international transfer", "R3", "Structuring", "R4", "Rapid pass-through", "R5", "Fan-in",
            "R6", "Fan-out");

    static String ruleName(String ruleId) {
        return RULES.getOrDefault(ruleId, "");
    }

    /** Display bands only; the score itself comes from F02's risk scorer. */
    static String riskLevel(int score) {
        return score >= 60 ? "HIGH" : score >= 45 ? "MED" : "LOW";
    }

    static String riskStyle(int score) {
        return "risk-" + riskLevel(score).toLowerCase();
    }

    /** An enum constant as words: IN_CASE reads "In case". */
    static String label(Enum<?> value) {
        if (value == null) {
            return "";
        }
        String words = value.name().replace('_', ' ').toLowerCase();
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }

    static String age(Duration d) {
        long minutes = d.toMinutes();
        if (minutes < 1) {
            return "now";
        }
        if (minutes < 60) {
            return minutes + "m";
        }
        if (minutes < 24 * 60) {
            return String.format("%dh %02dm", minutes / 60, minutes % 60);
        }
        return String.format("%dd %02dh", minutes / (24 * 60), minutes / 60 % 24);
    }

    /** Who acted, as the audit split shows it: the agent proposes, a human decides (Lucinity, four-eyes review). */
    static String actor(tracewise.domain.Actor actor) {
        return actor == null ? "" : actor.isHuman() ? "HUMAN" : actor.type().name();
    }

    /** The constant whose label() is text, or null (for "All" and other non-status choices). */
    static <E extends Enum<E>> E parse(Class<E> type, String text) {
        for (E e : type.getEnumConstants()) {
            if (label(e).equals(text)) {
                return e;
            }
        }
        return null;
    }
}
