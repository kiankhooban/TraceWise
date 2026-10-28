package tracewise.ui.cli;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tracewise.tools.ScenarioGenerator;

/** F12 from the CLI on the sample data: tracewise metrics [--model] [--rule] [--from] [--to] [--csv] [--json]. */
class MetricsCliTest {

    @TempDir Path dir;
    StringWriter out;
    StringWriter err;

    int run(String... args) {
        out = new StringWriter();
        err = new StringWriter();
        var all = new ArrayList<>(List.of("--db", dir.resolve("t.db").toString()));
        all.addAll(List.of(args));
        return TraceWiseCli.commandLine(new PrintWriter(out, true), new PrintWriter(err, true))
                .execute(all.toArray(String[]::new));
    }

    @BeforeEach
    void sampleData() throws Exception {
        assertEquals(0, run("import", Files.write(dir.resolve("s.csv"), ScenarioGenerator.generate(42)).toString()));
        assertEquals(0, run("monitor", "run"));
    }

    /** The sample data marks its planted schemes, so every rule's alert is labelled before any investigation. */
    @Test
    void beforeAnyRunOnlyThePerRulePrecisionIsAvailable() throws Exception {
        assertEquals(0, run("metrics"), err.toString());

        String text = out.toString();
        assertTrue(text.contains("0 investigation runs"), text);
        assertTrue(text.matches("(?s).*precision +not available.*"), text);
        assertTrue(text.matches("(?s).*R3 alert precision +1\\.000 \\(n=1, too few\\).*"), text);

        assertEquals(0, run("metrics", "--json"));
        var json = new ObjectMapper().readTree(out.toString());
        assertTrue(json.get(0).get("model").isNull());
        assertEquals(4, json.get(0).get("rulePrecision").size());
    }

    @Test
    void aReplayedInvestigationIsMeasuredForItsModel() {
        assertEquals(0, run("--llm", "replay", "--model", "claude-haiku-5-5", "investigate", "ALR-00001"), err.toString());

        assertEquals(0, run("metrics"), err.toString());
        String text = out.toString();
        assertTrue(text.contains("1 investigation run"), text);
        assertTrue(text.contains("claude-haiku-5-5"), text);
        assertTrue(text.matches("(?s).*\\nprecision +1\\.000 \\(n=1, too few\\) +1\\.000 \\(n=1, too few\\).*"), text);

        assertEquals(0, run("metrics", "--model", "claude-sonnet-5-5"));
        assertTrue(out.toString().contains("0 investigation runs"), out.toString());
    }

    @Test
    void theMetricsExportAsCsv() throws Exception {
        Path csv = dir.resolve("m.csv");

        assertEquals(0, run("metrics", "--rule", "R3", "--csv", csv.toString()), err.toString());

        String text = Files.readString(csv);
        assertTrue(text.startsWith("model,metric,value,sample_size,meaningful\r\n"), text);
        assertTrue(text.contains("all models,R3 alert precision,1.000,1,no\r\n"), text);
        assertFalse(text.contains("R4"), "only the chosen rule");
        assertTrue(out.toString().contains("written to " + csv), out.toString());
    }
}
