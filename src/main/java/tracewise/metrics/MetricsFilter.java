package tracewise.metrics;

import tracewise.domain.DateRange;

/**
 * F12 filters. range selects runs by when they finished, proposals by when they were made and alerts by when they
 * were raised; model and ruleId are null for all models or rules.
 */
public record MetricsFilter(DateRange range, String model, String ruleId) {

    public static final MetricsFilter ALL = new MetricsFilter(DateRange.ALL, null, null);

    public MetricsFilter withModel(String other) {
        return new MetricsFilter(range, other, ruleId);
    }
}
