package tracewise.ui.gui;

import java.util.ArrayList;
import java.util.List;
import tracewise.metrics.MetricRow;
import tracewise.metrics.PerformanceMetrics;

/** F12's metrics for the Performance screen: grouped, one column per model, each value with the n it rests on. */
final class PerformanceRows {

    private PerformanceRows() {
    }

    /** A group heading (rows empty) or one metric with its row from each model. */
    record Line(boolean group, String name, List<MetricRow> rows) {
        List<String> cells() {
            return rows.stream().map(r -> r.value() + "  n=" + r.sampleSize()).toList();
        }
    }

    /** perModel: each model's PerformanceMetrics.rows(); they share the same metrics in the same order. */
    static List<Line> of(List<List<MetricRow>> perModel) {
        var lines = new ArrayList<Line>();
        String current = null;
        for (int i = 0; i < perModel.get(0).size(); i++) {
            int row = i;
            String metric = perModel.get(0).get(i).metric();
            String group = group(metric);
            if (!group.equals(current)) {
                lines.add(new Line(true, group, List.of()));
                current = group;
            }
            lines.add(new Line(false, metric, perModel.stream().map(m -> m.get(row)).toList()));
        }
        return lines;
    }

    /** By the metric names PerformanceMetrics.rows() uses. */
    private static String group(String metric) {
        if (metric.endsWith("alert precision")) {
            return "Rules";
        }
        if (metric.contains("clean alert on a laundering account")) {
            return "Clean alert on a laundering account";
        }
        if (metric.startsWith("escalated") || metric.startsWith("dismissed") || metric.startsWith("inconclusive")) {
            return "Verdict against the label";
        }
        if (metric.startsWith("precision") || metric.startsWith("recall")) {
            return "Accuracy";
        }
        return "Agent runs";
    }

    /** muted when there is nothing to measure, wait (amber) when n is under the minimum sample. */
    static String style(MetricRow row) {
        if (PerformanceMetrics.NOT_AVAILABLE.equals(row.value())) {
            return "muted";
        }
        return row.meaningful() ? null : "wait";
    }
}
