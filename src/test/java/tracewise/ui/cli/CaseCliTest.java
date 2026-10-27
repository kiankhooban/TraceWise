package tracewise.ui.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tracewise.agent.AgentRunnerTest;
import tracewise.agent.AgentRunnerTest.ScriptedModel;
import tracewise.app.AppConfig;
import tracewise.app.TraceWiseBootstrap;
import tracewise.domain.ActorType;
import tracewise.llm.ModelConfig;
import tracewise.tools.ScenarioGenerator;

/** F08 from the CLI: case show, note, close, reopen, and audit export. */
class CaseCliTest {

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
    void aCaseUnderInvestigation() throws Exception {
        assertEquals(0, run("import", Files.write(dir.resolve("s.csv"), ScenarioGenerator.generate(42)).toString()));
        assertEquals(0, run("monitor", "run"));
        var model = new ScriptedModel().then(AgentRunnerTest.answer("```json\n{\"verdict\": \"INCONCLUSIVE\", "
                + "\"confidence\": 0.5, \"indicators\": [], \"cited_transactions\": [], \"totals\": [], "
                + "\"narrative\": \"Not enough to decide.\"}\n```"));
        try (var f = TraceWiseBootstrap.create(new AppConfig(dir.resolve("t.db"), "kian", ActorType.ANALYST,
                ModelConfig.of("anthropic", "claude-haiku-5-5", false), dir.resolve("replays"), dir.resolve(".env")),
                () -> model)) {
            f.investigate("ALR-00001", null);
        }
    }

    @Test
    void notesCloseReopenAndShow() {
        assertEquals(0, run("case", "note", "CAS-00001", "Called", "the", "branch."));
        assertEquals(0, run("case", "close", "CAS-00001", "--reason", "Explained by the holder"));
        assertTrue(run("case", "reopen", "CAS-00001") != 0, "a reason is required");
        assertEquals(0, run("case", "reopen", "CAS-00001", "--reason", "New deposits"));

        assertEquals(0, run("case", "show", "CAS-00001"));
        String shown = out.toString();
        for (String expected : List.of("CAS-00001", "OPEN", "INCONCLUSIVE", "Called the branch.", "New deposits",
                "Explained by the holder")) {
            assertTrue(shown.contains(expected), expected + " missing from:\n" + shown);
        }
    }

    @Test
    void anInvalidTransitionFailsWithTheState() {
        assertEquals(1, run("case", "reopen", "CAS-00001", "--reason", "why"));
        assertTrue(err.toString().contains("UNDER_INVESTIGATION"), err.toString());
    }

    @Test
    void auditExportWritesCsvForTheFilter() throws Exception {
        Path file = dir.resolve("audit.csv");
        assertEquals(0, run("audit", "export", "--event", "IMPORT", "--out", file.toString()));

        var lines = Files.readAllLines(file);
        assertEquals("timestamp,actor,actor_type,event_type,target,details,outcome", lines.get(0));
        assertEquals(2, lines.size());
        assertTrue(lines.get(1).contains(",IMPORT,"), lines.get(1));

        assertEquals(0, run("audit", "export", "--from", "2000-01-01", "--to", "2000-01-02"));
        assertEquals(1, out.toString().strip().split("\r?\n").length, "only the header: " + out);
        assertEquals(0, run("audit", "export", "--actor", "AGENT"));
        assertTrue(out.toString().contains("AGENT_STEP") || out.toString().contains("AGENT_FINISHED"), out.toString());
    }
}
