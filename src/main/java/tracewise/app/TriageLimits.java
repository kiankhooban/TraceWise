package tracewise.app;

import java.math.BigDecimal;
import tracewise.monitoring.AlertFilter;

/** Which alerts to triage and when to stop: after maxAlerts alerts, or once maxCostCad has been spent. */
public record TriageLimits(int maxAlerts, BigDecimal maxCostCad, AlertFilter filter) {

    public static final TriageLimits DEFAULT = new TriageLimits(50, new BigDecimal("5.00"), AlertFilter.ALL);

    public TriageLimits {
        if (maxAlerts < 1 || maxCostCad.signum() <= 0) {
            throw new IllegalArgumentException("Triage needs at least one alert and a cost limit above zero");
        }
    }
}
