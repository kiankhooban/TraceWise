package tracewise.ui.cli;

import static org.junit.jupiter.api.Assertions.*;

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

/** F09 from the CLI on the sample data: tracewise network <account> [--hops] [--from] [--to] [--case] [--json]. */
class NetworkCliTest {

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

    @Test
    void theStructuringAccountReceivesCashAndIsMarkedAlerted() {
        String account = ScenarioGenerator.accounts(42).get("S-C1");

        assertEquals(0, run("network", account));

        String text = out.toString();
        assertTrue(text.contains(account + "  hop 0  [alerted]"), text);
        assertTrue(text.contains("CASH -> " + account), text);
    }

    @Test
    void jsonHasNodesAndEdgesAndMoreHopsReachFurther() throws Exception {
        String account = ScenarioGenerator.accounts(42).get("P-C1");

        assertEquals(0, run("network", account, "--hops", "1", "--json"));
        var one = new com.fasterxml.jackson.databind.ObjectMapper().readTree(out.toString());
        assertEquals(account, one.get("rootAccountId").asText());
        assertTrue(one.get("edges").size() >= 2, out.toString());

        assertNotEquals(0, run("network", account, "--hops", "4"));
        assertNotEquals(0, run("network", "NOPE"));
    }

    @Test
    void aRangeWithNoTransfersSaysSo() {
        assertEquals(0, run("network", ScenarioGenerator.accounts(42).get("S-C1"), "--from", "2030-01-01", "--to",
                "2030-01-31"));
        assertTrue(out.toString().contains("No transfers in this period"), out.toString());
    }
}
