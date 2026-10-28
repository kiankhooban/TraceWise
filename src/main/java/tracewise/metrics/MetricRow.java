package tracewise.metrics;

/** One line of the dashboard, the CLI table and the CSV export: the value is already formatted. */
public record MetricRow(String metric, String value, int sampleSize) {

    public boolean meaningful() {
        return sampleSize >= Measure.MIN_SAMPLE;
    }
}
