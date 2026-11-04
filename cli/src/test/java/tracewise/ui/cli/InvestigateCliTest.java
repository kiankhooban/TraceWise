package tracewise.ui.cli;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tracewise.agent.AgentRunnerTest;
import tracewise.agent.AgentRunnerTest.ScriptedModel;
import tracewise.app.AppConfig;
import tracewise.app.TraceWiseBootstrap;
import tracewise.domain.ActorType;
import tracewise.domain.Alert;
import tracewise.llm.ModelConfig;
import tracewise.llm.RecordingLLMClient;
import tracewise.monitoring.AlertFilter;
import tracewise.persistence.Database;
import tracewise.persistence.SqliteAlertRepository;
import tracewise.persistence.SqliteTransactionRepository;
import tracewise.tools.ScenarioGenerator;

class InvestigateCliTest {

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

    /** Records a scripted investigation of the R3 alert through the app's own wiring, on a copy of the database. */
    String recordStructuringInvestigation(Path copy) {
        String alertId;
        String accountId;
        String finding;
        try (var db = Database.open(copy)) {
            var transactions = new SqliteTransactionRepository(db);
            Alert alert = new SqliteAlertRepository(db).find(new AlertFilter("R3", null, null)).get(0);
            var t = transactions.findTransaction(alert.transactionIds().get(0)).orElseThrow();
            alertId = alert.id();
            accountId = alert.accountId();
            finding = """
                    ```json
                    {"verdict": "ESCALATE", "confidence": 0.9, "indicators": [],
                     "cited_transactions": [{"transaction_id": "%s", "account_id": "%s", "amount_cad": %s,
                       "date": "%s"}], "totals": [], "narrative": "Deposits under the threshold."}
                    ```""".formatted(t.id(), accountId, t.amountCad().toPlainString(),
                    LocalDate.ofInstant(t.timestamp(), ZoneOffset.UTC));
        }
        var model = new ScriptedModel()
                .then(AgentRunnerTest.toolCall("account_history", "{\"account_id\":\"" + accountId + "\"}"))
                .then(AgentRunnerTest.answer(finding));
        var config = new AppConfig(copy, "rec", ActorType.ANALYST, ModelConfig.of("anthropic", "claude-haiku-5-5", true),
                dir.resolve("replays"), dir.resolve(".env"));
        try (var facade = TraceWiseBootstrap.create(config,
                () -> new RecordingLLMClient(model, TraceWiseBootstrap.replayStore(dir.resolve("replays"))))) {
            facade.investigate(alertId, null);
        }
        return alertId;
    }

    @Test
    void investigateReplaysARecordedRunWithoutAKey() throws Exception {
        Path db = dir.resolve("t.db");
        Path csv = Files.write(dir.resolve("s.csv"), ScenarioGenerator.generate(42));
        assertEquals(0, run(db, "import", csv.toString()));
        assertEquals(0, run(db, "monitor", "run"));
        Path copy = dir.resolve("copy.db");
        Files.copy(db, copy);
        String alertId = recordStructuringInvestigation(copy);

        assertEquals(0, run(db, "investigate", alertId, "--json"), err.toString());
        var json = new ObjectMapper().readTree(out.toString());
        assertEquals("COMPLETED", json.get("status").asText());
        assertEquals("CAS-" + alertId.substring(4), json.get("caseId").asText());
        assertEquals("ESCALATE", json.get("finding").get("verdict").asText());
        assertEquals("VERIFIED", json.get("finding").get("verification").get("status").asText());
        assertEquals(1, json.get("trace").get("steps").size());

        assertEquals(0, run(db, "investigate", alertId), err.toString());
        String text = out.toString();
        assertTrue(text.contains("account history"), "the step in words, not the tool's JSON: " + text);
        assertFalse(text.contains("{"), text);
        assertTrue(text.matches("(?s).*Verdict +ESCALATE  confidence .*"), text);
        assertTrue(text.matches("(?s).*Checked +VER 1/1 .*"), text);
    }

    @Test
    void unknownAlertFails() throws Exception {
        assertEquals(1, run(dir.resolve("t.db"), "investigate", "ALR-99999"));
        assertTrue(err.toString().contains("No alert ALR-99999"), err.toString());
    }

    /** Review 13: stderr may be a file while stdout is a terminal, so error lines carry no colour codes. */
    @Test
    void errorsStayPlainEvenWhenTheOutputIsColoured() {
        var out = new StringWriter();
        var err = new StringWriter();
        var cl = TraceWiseCli.commandLine(new PrintWriter(out, true), new PrintWriter(err, true));
        ((TraceWiseCli) cl.getCommand()).style = new Style(true, 80);
        assertEquals(1, cl.execute("--db", dir.resolve("t.db").toString(), "investigate", "ALR-99999"));
        assertTrue(err.toString().startsWith("Error: No alert ALR-99999"), err.toString());
    }
}
