package tracewise.ui.cli;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tracewise.agent.AgentLimits;
import tracewise.agent.AgentRunner;
import tracewise.agent.AgentRunnerTest;
import tracewise.agent.AgentRunnerTest.ScriptedModel;
import tracewise.agent.tools.AccountHistoryTool;
import tracewise.agent.tools.CounterpartiesTool;
import tracewise.agent.tools.ToolRegistry;
import tracewise.app.InvestigationService;
import tracewise.audit.AuditLog;
import tracewise.audit.SqliteAuditRepository;
import tracewise.domain.Actor;
import tracewise.domain.ActorType;
import tracewise.domain.Alert;
import tracewise.llm.ModelConfig;
import tracewise.llm.RecordingLLMClient;
import tracewise.llm.ReplayStore;
import tracewise.monitoring.AlertFilter;
import tracewise.persistence.Database;
import tracewise.persistence.SqliteAlertRepository;
import tracewise.persistence.SqliteCaseRepository;
import tracewise.persistence.SqliteTransactionRepository;
import tracewise.tools.ScenarioGenerator;
import tracewise.verification.ClaimVerifier;

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

    /** Records a scripted investigation of the R3 alert through the service, on a copy of the database. */
    String recordStructuringInvestigation(Path copy) {
        try (var db = Database.open(copy)) {
            var transactions = new SqliteTransactionRepository(db);
            var alerts = new SqliteAlertRepository(db);
            Alert alert = alerts.find(new AlertFilter("R3", null, null)).get(0);
            var t = transactions.findTransaction(alert.transactionIds().get(0)).orElseThrow();
            var model = new ScriptedModel()
                    .then(AgentRunnerTest.toolCall("account_history", "{\"account_id\":\"" + alert.accountId() + "\"}"))
                    .then(AgentRunnerTest.answer("""
                            ```json
                            {"verdict": "ESCALATE", "confidence": 0.9, "indicators": ["Structuring"],
                             "cited_transactions": [{"transaction_id": "%s", "account_id": "%s", "amount_cad": %s,
                               "date": "%s"}], "totals": [], "narrative": "Deposits under the threshold."}
                            ```""".formatted(t.id(), alert.accountId(), t.amountCad().toPlainString(),
                            LocalDate.ofInstant(t.timestamp(), ZoneOffset.UTC))));
            var tools = new ToolRegistry();
            tools.register(new AccountHistoryTool(transactions));
            tools.register(new CounterpartiesTool(transactions));
            var runner = new AgentRunner(new RecordingLLMClient(model, new ReplayStore(dir.resolve("replays"))), tools,
                    AgentLimits.DEFAULT);
            new InvestigationService(db, alerts, new SqliteCaseRepository(db), () -> runner,
                    new ClaimVerifier(transactions), new AuditLog(new SqliteAuditRepository(db)),
                    new Actor("rec", ActorType.ANALYST), ModelConfig.of("anthropic", "claude-haiku-5-5", true),
                    BigDecimal.ONE).investigate(alert.id(), null);
            return alert.id();
        }
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
        assertTrue(text.contains("account_history"), text);
        assertTrue(text.contains("Verdict: ESCALATE"), text);
        assertTrue(text.contains("1 of 1 claims confirmed"), text);
    }

    @Test
    void unknownAlertFails() throws Exception {
        assertEquals(1, run(dir.resolve("t.db"), "investigate", "ALR-99999"));
        assertTrue(err.toString().contains("No alert ALR-99999"), err.toString());
    }
}
