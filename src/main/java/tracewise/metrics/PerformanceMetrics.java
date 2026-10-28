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
        int falseNegatives, int trueNegatives, int inconclusive, int inconclusiveLaundering, Measure precision,
        Measure recall, Measure strictRecall,
        Measure verificationRate, Measure averageTurns, BigDecimal averageCostCad, Measure failureRate,
        Measure overrideRate, List<RulePrecision> rulePrecision) {

    public static final String NOT_AVAILABLE = "not available";

    public PerformanceMetrics {
        rulePrecision = List.copyOf(rulePrecision);
    }

    /** "all models" or the model's run label. */
    public String label() {
        return model == null ? "all models" : model;
    }

    /**
     * The metrics in display order, as the dashboard, the CLI and the CSV show them. Turns and cost cover runs that
     * produced a finding: a failed run stores none (the failure rate counts those).
     */
    public List<MetricRow> rows() {
        int labelled = truePositives + falsePositives + falseNegatives + trueNegatives + inconclusive;
        var rows = new ArrayList<MetricRow>(List.of(rate("precision", precision), rate("recall", recall),
                rate("recall (inconclusive counts as missed)", strictRecall),
                count("escalated and laundering", truePositives, labelled),
                count("escalated but not laundering", falsePositives, labelled),
                count("dismissed but laundering", falseNegatives, labelled),
                count("dismissed and not laundering", trueNegatives, labelled),
                count("inconclusive and laundering", inconclusiveLaundering, labelled),
                count("inconclusive and not laundering", inconclusive - inconclusiveLaundering, labelled),
                rate("verification rate", verificationRate),
                new MetricRow("average turns (runs with a finding)", averageTurns.value() == null ? NOT_AVAILABLE
                        : String.format(Locale.ROOT, "%.1f", averageTurns.value()), averageTurns.sampleSize()),
                new MetricRow("average cost in CAD (runs with a finding)", averageCostCad == null ? NOT_AVAILABLE
                        : averageCostCad.setScale(4, RoundingMode.HALF_UP).toPlainString(), sampleSize),
                rate("failure rate", failureRate), rate("human override rate", overrideRate)));
        rulePrecision.forEach(r -> rows.add(rate(r.ruleId() + " alert precision", r.precision())));
        return rows;
    }

    /** Header (metric, then each model with its run count) and one row per metric, as the CLI and GUI show them. */
    public static List<List<String>> table(List<PerformanceMetrics> metrics) {
        var header = new ArrayList<>(List.of("metric"));
        metrics.forEach(m -> header.add(m.label() + " (" + m.sampleSize() + (m.sampleSize() == 1 ? " run)" : " runs)")));
        var table = new ArrayList<List<String>>(List.of(header));
        // every model has the same rows in the same order: rule precision does not depend on the model
        List<MetricRow> first = metrics.get(0).rows();
        for (int i = 0; i < first.size(); i++) {
            var line = new ArrayList<>(List.of(first.get(i).metric()));
            for (PerformanceMetrics m : metrics) {
                line.add(m.rows().get(i).display());
            }
            table.add(line);
        }
        return table;
    }

    private static MetricRow count(String metric, int n, int labelled) {
        return new MetricRow(metric, String.valueOf(n), labelled);
    }

    private static MetricRow rate(String metric, Measure m) {
        return new MetricRow(metric, m.value() == null ? NOT_AVAILABLE : String.format(Locale.ROOT, "%.3f", m.value()),
                m.sampleSize());
    }
}
