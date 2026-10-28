package tracewise.metrics;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * F12 result for one model (null: all models). sampleSize is the number of investigation runs (findings); the
 * verdict counts compare each case's latest verdict with its label. averageCostCad is null without findings.
 */
public record PerformanceMetrics(String model, int sampleSize, int truePositives, int falsePositives,
        int falseNegatives, int trueNegatives, int inconclusive, Measure precision, Measure recall,
        Measure verificationRate, Measure averageTurns, BigDecimal averageCostCad, Measure failureRate,
        Measure overrideRate, List<RulePrecision> rulePrecision) {

    public static final String NOT_AVAILABLE = "not available";

    public PerformanceMetrics {
        rulePrecision = List.copyOf(rulePrecision);
    }

    /** The metrics in display order, as the dashboard, the CLI and the CSV show them. */
    public List<MetricRow> rows() {
        var rows = new ArrayList<MetricRow>(List.of(rate("precision", precision), rate("recall", recall),
                rate("verification rate", verificationRate),
                new MetricRow("average turns", averageTurns.value() == null ? NOT_AVAILABLE
                        : String.format(Locale.ROOT, "%.1f", averageTurns.value()), averageTurns.sampleSize()),
                new MetricRow("average cost (CAD)", averageCostCad == null ? NOT_AVAILABLE
                        : averageCostCad.setScale(4, RoundingMode.HALF_UP).toPlainString(), sampleSize),
                rate("failure rate", failureRate), rate("human override rate", overrideRate)));
        rulePrecision.forEach(r -> rows.add(rate(r.ruleId() + " alert precision", r.precision())));
        return rows;
    }

    private static MetricRow rate(String metric, Measure m) {
        return new MetricRow(metric, m.value() == null ? NOT_AVAILABLE : String.format(Locale.ROOT, "%.3f", m.value()),
                m.sampleSize());
    }
}
