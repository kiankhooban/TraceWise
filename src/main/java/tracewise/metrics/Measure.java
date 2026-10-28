package tracewise.metrics;

/** F12: one metric with the number of cases, runs or alerts it is based on; value is null when there are none. */
public record Measure(Double value, int sampleSize) {

    /** Fewer than this many and the design marks the figure as not meaningful. */
    public static final int MIN_SAMPLE = 10;

    public static Measure ratio(int part, int whole) {
        return new Measure(whole == 0 ? null : (double) part / whole, whole);
    }

    public boolean meaningful() {
        return sampleSize >= MIN_SAMPLE;
    }
}
