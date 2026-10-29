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
import tracewise.agent.AgentRunnerTest;
import tracewise.agent.AgentRunnerTest.ScriptedModel;
import tracewise.app.AppConfig;
import tracewise.app.EscalatedCase;
import tracewise.app.TraceWiseBootstrap;
import tracewise.domain.ActorType;
import tracewise.llm.ModelConfig;

/** F11 from the CLI: report show, edit, finalise (then approvals approve) and export. */
class ReportCliTest {

    @TempDir Path dir;
    StringWriter out;
    StringWriter err;
    EscalatedCase setup;

    int run(String... args) {
        out = new StringWriter();
        err = new StringWriter();
        var all = new ArrayList<>(List.of("--db", dir.resolve("t.db").toString()));
        all.addAll(List.of(args));
        return TraceWiseCli.commandLine(new PrintWriter(out, true), new PrintWriter(err, true))
                .execute(all.toArray(String[]::new));
    }

    /** The draft is made through the facade, where the test can script the model. */
    @BeforeEach
    void aDraftedReport() throws Exception {
        var model = new ScriptedModel();
        try (var f = TraceWiseBootstrap.create(new AppConfig(dir.resolve("t.db"), "kian", ActorType.ANALYST,
                ModelConfig.of("anthropic", "claude-haiku-5-5", false), dir.resolve("replays"), dir.resolve(".env")),
                () -> model)) {
            setup = EscalatedCase.create(f, dir, dir.resolve("t.db"), model);
            model.then(AgentRunnerTest.answer("Deposits " + setup.cited().id() + " and TX-99999."));
            f.draftReport("CAS-00001", false);
        }
    }

    @Test
    void showEditFinaliseApproveAndExport() throws Exception {
        assertEquals(0, run("report", "show", "CAS-00001"));
        assertTrue(out.toString().contains("DRAFT") && out.toString().contains(setup.cited().id()), out.toString());

        assertNotEquals(0, run("report", "finalise", "CAS-00001"));
        assertTrue(err.toString().contains("TX-99999"), err.toString());

        Path grounds = Files.writeString(dir.resolve("grounds.txt"), "Deposits " + setup.cited().id() + " only.");
        assertEquals(0, run("report", "edit", "CAS-00001", "--grounds-file", grounds.toString()));
        assertNotEquals(0, run("report", "export", "CAS-00001", "--out", dir.resolve("out").toString()));

        assertEquals(0, run("report", "finalise", "CAS-00001"));
        String pendingId = out.toString().replaceAll("(?s).*?(APR-\\d+).*", "$1");
        assertEquals(0, run("--role", "supervisor", "approvals", "approve", pendingId));

        assertEquals(0, run("report", "show", "CAS-00001", "--json"));
        assertTrue(out.toString().contains("\"status\" : \"FINAL\"") || out.toString().contains("\"status\":\"FINAL\""),
                out.toString());
        assertFalse(out.toString().contains("label"));

        assertEquals(0, run("report", "export", "CAS-00001", "--format", "json", "--out", dir.resolve("out").toString()));
        assertTrue(Files.exists(dir.resolve("out/CAS-00001-STR.json")));
        assertFalse(Files.exists(dir.resolve("out/CAS-00001-STR.html")));
        assertEquals(0, run("report", "export", "CAS-00001", "--out", dir.resolve("out").toString()));
        assertTrue(Files.exists(dir.resolve("out/CAS-00001-STR.html")));
    }

    /** Findings 2 and 8: --format both writes both files; draft refuses to replace a draft without --replace. */
    @Test
    void exportBothAndDraftNeedsReplace() throws Exception {
        assertNotEquals(0, run("report", "draft", "CAS-00001"));
        assertTrue(err.toString().contains("--replace"), err.toString());

        assertEquals(0, run("report", "edit", "CAS-00001", "--grounds-file",
                Files.writeString(dir.resolve("g.txt"), "Deposits " + setup.cited().id() + ".").toString()));
        assertEquals(0, run("report", "finalise", "CAS-00001"));
        assertEquals(0, run("approvals", "approve", out.toString().replaceAll("(?s).*?(APR-\\d+).*", "$1")));
        assertEquals(0, run("report", "export", "CAS-00001", "--format", "both", "--out", dir.resolve("b").toString()));
        assertTrue(Files.exists(dir.resolve("b/CAS-00001-STR.html")) && Files.exists(dir.resolve("b/CAS-00001-STR.json")));
    }
}
