package tracewise.monitoring;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import tracewise.domain.Transaction;

/** One window in which a rule fired for one account. */
public record RuleMatch(String ruleId, RuleKind kind, String accountId, List<Transaction> transactions,
        BigDecimal totalCad, Instant windowStart, Instant windowEnd) {
}
