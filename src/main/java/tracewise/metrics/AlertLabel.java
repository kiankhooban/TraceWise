package tracewise.metrics;

import java.time.Instant;

/**
 * An alert's ground truth: laundering if any of its transactions is labelled so; null when none has a label.
 * accountLaundering says whether the alert's account has any labelled laundering transaction at all (null: no labels).
 */
public record AlertLabel(String alertId, String ruleId, Instant createdAt, Boolean laundering,
        Boolean accountLaundering) {
}
