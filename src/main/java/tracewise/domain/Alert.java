package tracewise.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * A suspicion-rule match waiting for investigation. id is null until the alert is stored.
 * priority: 1 high, 2 medium, 3 low.
 */
public record Alert(String id, String ruleId, String accountId, List<String> transactionIds, BigDecimal totalCad,
        int riskScore, int priority, AlertStatus status, Instant createdAt) {

    public Alert {
        transactionIds = List.copyOf(transactionIds);
    }
}
