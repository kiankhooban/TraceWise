package tracewise.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** A Threshold Report Register entry (R1, R2): reportable whether or not anything is suspicious. */
public record ThresholdReport(String id, String ruleId, String accountId, List<String> transactionIds,
        BigDecimal totalCad, Instant windowStart, Instant windowEnd) {

    public ThresholdReport {
        transactionIds = List.copyOf(transactionIds);
    }
}
