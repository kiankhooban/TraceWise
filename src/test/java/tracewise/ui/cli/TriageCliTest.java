package tracewise.ui.cli;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tracewise.agent.AgentRunnerTest;
import tracewise.agent.AgentRunnerTest.ScriptedModel;
import tracewise.app.AppConfig;
import tracewise.app.TraceWiseBootstrap;
import tracewise.app.TriageLimits;
import tracewise.domain.ActorType;
import tracewise.llm.ModelConfig;
import tracewise.llm.RecordingLLMClient;
import tracewise.llm.ReplayStore;
import tracewise.tools.ScenarioGenerator;

class TriageCliTest {

    @TempDir Path dir;
    StringWriter out;
    StringWriter err;

    int run(Path db, String... args) {
        out = new StringWriter();
        err = new StringWriter();
        var all = new ArrayList<>(List.of("--db", db.toString(), "--llm", "replay", "--model", "claude-haiku-5-5",
                "--replays", dir.resolve("replays").toString()));
        all.addAll(List.of(args));
        return TraceWiseCli.commandLine(new PrintWriter(out, true), new PrintWriter(err, true))
                .execute(all.toArray(String[]::new));
    }

    static String answer(String verdict) {
        return "{\"verdict\":\"" + verdict + "\",\"confidence\":0.7,\"indicators\":[],\"cited_transactions\":[],"
                + "\"totals\":[],\"narrative\":\"n\"}";
    }

    @Test
    void triageReplaysARecordedRunAndPrintsTheSummary() throws Exception {
        Path db = dir.resolve("t.db");
        Path csv = Files.write(dir.resolve("s.csv"), ScenarioGenerator.generate(42));
        assertEquals(0, run(db, "import", csv.toString()));
        assertEquals(0, run(db, "monitor", "run"));
        Path copy = dir.resolve("copy.db");
        Files.copy(db, copy);

        // record a two-alert triage through the app's own wiring, on the copy
        var model = new ScriptedModel()
                .then(AgentRunnerTest.toolCall("propose_escalation", "{\"case_id\":\"CAS-00002\",\"justification\":\"j\"}"))
                .then(AgentRunnerTest.answer(answer("ESCALATE")))
                .then(AgentRunnerTest.answer(answer("DISMISS")));
        var config = new AppConfig(copy, "rec", ActorType.ANALYST, ModelConfig.of("anthropic", "claude-haiku-5-5", true),
                dir.resolve("replays"), dir.resolve(".env"));
        try (var facade = TraceWiseBootstrap.create(config,
                () -> new RecordingLLMClient(model, new ReplayStore(dir.resolve("replays"))))) {
            facade.triage(new TriageLimits(2, new java.math.BigDecimal("5.00"), tracewise.monitoring.AlertFilter.ALL), null);
        }

        assertEquals(0, run(db, "triage", "--max-alerts", "2"), err.toString());
        String text = out.toString();
        assertTrue(text.contains("ALR-00002") && text.contains("ALR-00001"), text);
        assertTrue(text.contains("Processed 2 alerts (0 failed)"), text);
        assertTrue(text.contains("1 escalations and 0 closes await approval"), text);
        assertTrue(text.contains("Alert limit reached"), text);

        assertEquals(0, run(db, "triage", "--json"), err.toString());
        var json = new ObjectMapper().readTree(out.toString());
        assertEquals("FAILED", json.get("items").get(0).get("status").asText(), "ALR-00004 was never recorded, so replay fails it");
        assertTrue(json.get("summary").asText().startsWith("Processed 2 alerts (2 failed)"), json.get("summary").asText());
    }

    @Test
    void rejectsLimitsBelowOne() {
        assertEquals(1, run(dir.resolve("t.db"), "triage", "--max-alerts", "0"));
        assertTrue(err.toString().contains("at least one alert"), err.toString());
    }
}
