package tracewise.metrics;

/** One line of the dashboard, the CLI table and the CSV export: the value is already formatted. */
public record MetricRow(String metric, String value, int sampleSize) {

    public boolean meaningful() {
        return sampleSize >= Measure.MIN_SAMPLE;
    }

    /** The value with its sample size, marked when it is too small to mean much. */
    public String display() {
        return value + " (n=" + sampleSize + (meaningful() ? ")" : ", too few)");
    }
}
