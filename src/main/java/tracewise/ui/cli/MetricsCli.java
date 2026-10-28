package tracewise.ui.cli;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import tracewise.domain.DateRange;
import tracewise.metrics.Measure;
import tracewise.metrics.MetricsCsv;
import tracewise.metrics.MetricsFilter;
import tracewise.metrics.PerformanceMetrics;

@Command(name = "metrics", description = "How well the agent performs: precision and recall against labels, "
        + "verification, turns, cost, failures, human overrides and per-rule alert precision (F12).")
public class MetricsCli extends CliCommandBase {

    @Option(names = "--model", description = "Only this model's runs, for example claude-sonnet-5-5 (default: all "
            + "models, then each model).")
    String model;

    @Option(names = "--rule", description = "Only cases and alerts of this rule, for example R4.")
    String rule;

    @Option(names = "--from", description = "First day included, YYYY-MM-DD (UTC).")
    LocalDate from;

    @Option(names = "--to", description = "Last day included, YYYY-MM-DD (UTC).")
    LocalDate to;

    @Option(names = "--csv", description = "Also write the metrics to this CSV file.")
    Path csv;

    @Override
    protected Object execute() {
        var filter = new MetricsFilter(DateRange.ofDays(from, to), model, rule);
        List<PerformanceMetrics> metrics = model == null ? facade().metricsByModel(filter)
                : List.of(facade().computeMetrics(filter));
        if (csv != null) {
            try (var w = Files.newBufferedWriter(csv, StandardCharsets.UTF_8)) {
                MetricsCsv.write(metrics, w);
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot write " + csv + ": " + e.getMessage(), e);
            }
        }
        return metrics;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void print(Object result) {
        if (json) {
            super.print(result);
            return;
        }
        var metrics = (List<PerformanceMetrics>) result;
        int runs = metrics.get(0).sampleSize();
        out().println("Agent performance: " + runs + (runs == 1 ? " investigation run" : " investigation runs"));
        out().println();
        List<List<String>> table = PerformanceMetrics.table(metrics);
        int[] widths = new int[table.get(0).size()];
        table.forEach(line -> {
            for (int c = 0; c < line.size(); c++) {
                widths[c] = Math.max(widths[c], line.get(c).length());
            }
        });
        for (List<String> line : table) {
            var text = new StringBuilder();
            for (int c = 0; c < line.size(); c++) {
                text.append(c == line.size() - 1 ? line.get(c) : String.format("%-" + (widths[c] + 2) + "s", line.get(c)));
            }
            out().println(text);
        }
        out().println();
        out().println("n is what a figure is based on; under " + Measure.MIN_SAMPLE
                + " it is marked too few and is not meaningful.");
        if (csv != null) {
            out().println("Metrics written to " + csv);
        }
    }
}
