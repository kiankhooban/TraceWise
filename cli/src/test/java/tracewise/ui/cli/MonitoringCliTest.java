package tracewise.ui.cli;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
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

class MonitoringCliTest {

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
    void importScenarios() throws IOException {
        Path file = Files.write(dir.resolve("s.csv"), ScenarioGenerator.generate(42));
        assertEquals(0, run("import", file.toString()));
    }

    @Test
    void monitorRunThenListAlertsAndThresholdReports() throws IOException {
        assertEquals(0, run("monitor", "run"));
        assertTrue(out.toString().contains("4 threshold reports; 4 new alerts: R3 1, R4 1, R5 1, R6 1"), out.toString());

        assertEquals(0, run("alerts", "list", "--rule", "R3"));
        assertTrue(out.toString().contains("ALR-") && out.toString().contains(ScenarioGenerator.accounts(42).get("S-C1")), out.toString());

        assertEquals(0, run("alerts", "list", "--json"));
        assertEquals(4, new ObjectMapper().readTree(out.toString()).size());

        assertEquals(0, run("threshold-reports", "list"));
        var ids = ScenarioGenerator.accounts(42);
        assertTrue(out.toString().contains(ids.get("L-C1")) && out.toString().contains(ids.get("I-C2")), out.toString());
    }

    @Test
    void monitoringBeforeImportPointsToImport() {
        out = new StringWriter();
        err = new StringWriter();
        int code = TraceWiseCli.commandLine(new PrintWriter(out, true), new PrintWriter(err, true))
                .execute("--db", dir.resolve("empty.db").toString(), "monitor", "run");
        assertEquals(1, code);
        assertEquals("Error: no transactions loaded; import a file first", err.toString().strip());
    }

    @Test
    void rulesCanBeListedAndChangedOnlyBySupervisor() {
        assertEquals(0, run("rules", "list"));
        assertTrue(out.toString().contains("R3") && out.toString().contains("window_days=7"), out.toString());

        assertEquals(1, run("rules", "set", "R5", "--set", "min_senders=7"));
        assertTrue(err.toString().contains("only a Compliance Supervisor"), err.toString());

        assertEquals(0, run("--role", "supervisor", "rules", "set", "R5", "--set", "min_senders=7"));
        assertEquals(1, run("--role", "supervisor", "rules", "set", "R3", "--set", "lower=12000"));
        assertTrue(err.toString().contains("R3: lower must be below upper"), err.toString());

        assertEquals(0, run("--role", "supervisor", "rules", "set", "R6", "--disable"));
        assertEquals(0, run("rules", "list"));
        assertTrue(out.toString().contains("min_senders=7"), out.toString());
        assertTrue(out.toString().matches("(?s).*R6\\s+disabled.*"), out.toString());
    }
}
