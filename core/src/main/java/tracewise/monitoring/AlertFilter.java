package tracewise.monitoring;

import java.math.BigDecimal;
import tracewise.domain.AlertStatus;

/** Null fields match everything. minTotalCad keeps alerts at or above that amount. */
public record AlertFilter(String ruleId, AlertStatus status, BigDecimal minTotalCad) {
    public static final AlertFilter ALL = new AlertFilter(null, null, null);
}
