package tracewise.agent;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** The summary of one triage run, with one item per alert it attempted, in the order attempted. */
public record TriageReport(EndReason endReason, List<Item> items, BigDecimal costCad, long durationMs,
        int escalationsProposed, int closesProposed, int alertsLinked, int otherActions) {

    public enum EndReason {
        COMPLETED, STOPPED, ALERT_LIMIT, COST_LIMIT, REPEATED_FAILURES, NOTHING_TO_TRIAGE
    }

    /** verdict and verification are null when the investigation produced no finding; message says why. */
    public record Item(String alertId, String caseId, OutcomeStatus status, Verdict verdict, String verification,
            String message, BigDecimal costCad) {

        boolean failed() {
            return status != OutcomeStatus.COMPLETED && status != OutcomeStatus.INCOMPLETE;
        }
    }

    /** Triage stops after this many alerts fail one after another, which usually means the model is unreachable. */
    public static final int MAX_FAILURES_IN_A_ROW = 3;

    public TriageReport {
        items = List.copyOf(items);
    }

    public int processed() {
        return items.size();
    }

    public int failed() {
        return (int) items.stream().filter(Item::failed).count();
    }

    /** "ALR-00004: The model call failed: overloaded" for each failed alert. */
    public List<String> failures() {
        return items.stream().filter(Item::failed).map(i -> i.alertId() + ": " + i.message()).toList();
    }

    public String summary() {
        if (endReason == EndReason.NOTHING_TO_TRIAGE) {
            return "Nothing to triage";
        }
        String ending = switch (endReason) {
            case STOPPED -> " Stopped by the analyst; the remaining alerts stay open.";
            case ALERT_LIMIT -> " Alert limit reached; the remaining alerts stay open.";
            case COST_LIMIT -> " Cost limit reached; the remaining alerts stay open.";
            case REPEATED_FAILURES -> " Stopped after " + TriageReport.MAX_FAILURES_IN_A_ROW
                    + " failures in a row; check the model connection. The remaining alerts stay open.";
            default -> "";
        };
        return "Processed " + processed() + " alerts (" + failed() + " failed). Linked " + alertsLinked
                + " alerts into cases. " + escalationsProposed + " escalations and " + closesProposed
                + " closes await approval. " + otherActions + " other actions taken. Cost CAD "
                + costCad.setScale(4, RoundingMode.HALF_UP).toPlainString() + "." + ending;
    }
}
