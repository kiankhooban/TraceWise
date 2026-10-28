package tracewise.metrics;

import java.time.Instant;

/** An alert's ground truth: laundering if any of its transactions is labelled so; null when none has a label. */
public record AlertLabel(String alertId, String ruleId, Instant createdAt, Boolean laundering) {
}
