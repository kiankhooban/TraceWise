package tracewise.ui.gui;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import tracewise.metrics.MetricRow;

/** F12 Performance screen: metrics in named groups, the sample size shown once per cell instead of "too few" text. */
class PerformanceRowsTest {

    static final List<MetricRow> ROWS = List.of(new MetricRow("precision", "1.000", 3), new MetricRow("recall", "0.900", 12),
            new MetricRow("escalated and laundering", "3", 4),
            new MetricRow("escalated, clean alert on a laundering account", "0", 0),
            new MetricRow("verification rate", "1.000", 4), new MetricRow("human override rate", "not available", 0),
            new MetricRow("R3 alert precision", "1.000", 1));

    @Test
    void eachGroupHeadsItsMetricsOnce() {
        var lines = PerformanceRows.of(List.of(ROWS));
        assertEquals(List.of("Accuracy", "precision", "recall", "Verdict against the label", "escalated and laundering",
                "Clean alert on a laundering account", "escalated, clean alert on a laundering account", "Agent runs",
                "verification rate", "human override rate", "Rules", "R3 alert precision"),
                lines.stream().map(PerformanceRows.Line::name).toList());
        assertTrue(lines.get(0).group());
        assertFalse(lines.get(1).group());
    }

    @Test
    void aCellShowsItsSampleSizeAndIsMarkedWhenTooSmall() {
        var lines = PerformanceRows.of(List.of(ROWS, ROWS));
        var precision = lines.get(1);
        assertEquals(List.of("1.000  n=3", "1.000  n=3"), precision.cells());
        assertEquals("wait", PerformanceRows.style(ROWS.get(0)));
        assertNull(PerformanceRows.style(ROWS.get(1)), "n=12 is enough");
        assertEquals("muted", PerformanceRows.style(ROWS.get(5)), "nothing to measure");
    }
}
