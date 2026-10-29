package tracewise.metrics;

import java.io.IOException;
import java.io.Writer;
import java.util.List;

/** F12 export: one CSV row per model and metric (CRLF line ends, like the audit export). */
public final class MetricsCsv {

    private MetricsCsv() {
    }

    /** No field needs quoting: model labels come from the price list (plus " (replay)"), metric names and values are TraceWise's own. */
    public static void write(List<PerformanceMetrics> metrics, Writer out) throws IOException {
        out.write("model,metric,value,sample_size,meaningful\r\n");
        for (PerformanceMetrics m : metrics) {
            for (MetricRow row : m.rows()) {
                out.write(String.join(",", m.label(), row.metric(), row.value(),
                        String.valueOf(row.sampleSize()), row.meaningful() ? "yes" : "no") + "\r\n");
            }
        }
        out.flush();
    }
}
