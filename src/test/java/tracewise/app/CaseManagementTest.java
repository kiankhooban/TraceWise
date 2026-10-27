package tracewise.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tracewise.agent.AgentRunnerTest;
import tracewise.agent.AgentRunnerTest.ScriptedModel;
import tracewise.audit.AuditFilter;
import tracewise.cases.state.CaseStatus;
import tracewise.domain.ActorType;
import tracewise.domain.AlertStatus;
import tracewise.llm.ModelConfig;
import tracewise.tools.ScenarioGenerator;

/** F08: an analyst's notes, closing and reopening, each through the dispatcher and audited. */
class CaseManagementTest {

    static final String INCONCLUSIVE = "```json\n{\"verdict\": \"INCONCLUSIVE\", \"confidence\": 0.5, \"indicators\": [], "
            + "\"cited_transactions\": [], \"totals\": [], \"narrative\": \"Not enough to decide.\"}\n```";

    @TempDir Path dir;
    final ScriptedModel model = new ScriptedModel();

    TraceWiseFacade facade() {
        return TraceWiseBootstrap.create(new AppConfig(dir.resolve("t.db"), "kian", ActorType.ANALYST,
                ModelConfig.of("anthropic", "claude-haiku-5-5", false), dir.resolve("replays"), dir.resolve(".env")),
                () -> model);
    }

    @BeforeEach
    void aCaseUnderInvestigation() throws Exception {
        model.then(AgentRunnerTest.answer(INCONCLUSIVE));
        try (var f = facade()) {
            f.importTransactions(Files.write(dir.resolve("s.csv"), ScenarioGenerator.generate(42)));
            f.runMonitoring();
            f.investigate("ALR-00001", null);
        }
    }

    @Test
    void aNoteIsKeptWithItsAuthorAndAudited() {
        try (var f = facade()) {
            f.addNote("CAS-00001", "Called the branch: deposits made by the account holder.");

            var note = f.getCase("CAS-00001").notes().get(0);
            assertEquals("kian", note.author());
            assertEquals("Called the branch: deposits made by the account holder.", note.text());
            assertEquals(1, f.queryAudit(new AuditFilter("ACTION_EXECUTED", "CAS-00001")).size());
            assertThrows(IllegalArgumentException.class, () -> f.addNote("CAS-00001", " "));
        }
    }

    @Test
    void anAnalystClosesAndReopensACaseWithReasons() {
        try (var f = facade()) {
            f.closeCase("CAS-00001", "Deposits explained by the account holder");
            assertEquals(CaseStatus.CLOSED, f.getCase("CAS-00001").status());
            assertEquals(AlertStatus.CLOSED, f.listAlerts(tracewise.monitoring.AlertFilter.ALL).stream()
                    .filter(a -> a.id().equals("ALR-00001")).findFirst().orElseThrow().status());

            assertThrows(IllegalArgumentException.class, () -> f.reopenCase("CAS-00001", " "));
            f.reopenCase("CAS-00001", "New deposits arrived");

            assertEquals(CaseStatus.OPEN, f.getCase("CAS-00001").status());
            assertEquals(AlertStatus.IN_CASE, f.listAlerts(tracewise.monitoring.AlertFilter.ALL).stream()
                    .filter(a -> a.id().equals("ALR-00001")).findFirst().orElseThrow().status());
            var details = f.queryAudit(new AuditFilter("ACTION_EXECUTED", "CAS-00001")).stream()
                    .map(e -> e.details()).toList();
            assertTrue(details.stream().anyMatch(d -> d.contains("New deposits arrived")), details.toString());
            assertTrue(details.stream().anyMatch(d -> d.contains("Deposits explained")), details.toString());
        }
    }

    @Test
    void anInvalidTransitionNamesTheCurrentState() {
        try (var f = facade()) {
            var e = assertThrows(IllegalStateException.class, () -> f.reopenCase("CAS-00001", "why not"));
            assertTrue(e.getMessage().contains("UNDER_INVESTIGATION"), e.getMessage());
        }
    }

    @Test
    void aCaseWaitingForApprovalIsClosedByDecidingTheProposal() {
        model.then(AgentRunnerTest.toolCall("propose_close", "{\"case_id\":\"CAS-00001\",\"justification\":\"j\"}"))
                .then(AgentRunnerTest.answer(INCONCLUSIVE.replace("INCONCLUSIVE", "DISMISS")));
        try (var f = facade()) {
            f.investigate("ALR-00001", null);
            assertEquals(CaseStatus.PENDING_APPROVAL, f.getCase("CAS-00001").status());

            var e = assertThrows(IllegalStateException.class, () -> f.closeCase("CAS-00001", "false alarm"));
            assertTrue(e.getMessage().contains("approval"), e.getMessage());
        }
    }

    @Test
    void aReopenedCaseIsTriagedAgain() {
        model.then(AgentRunnerTest.answer(INCONCLUSIVE)).then(AgentRunnerTest.answer(INCONCLUSIVE))
                .then(AgentRunnerTest.answer(INCONCLUSIVE)).then(AgentRunnerTest.answer(INCONCLUSIVE));
        try (var f = facade()) {
            f.closeCase("CAS-00001", "Explained");
            f.reopenCase("CAS-00001", "New deposits arrived");

            var report = f.triage(TriageLimits.DEFAULT, null);

            assertTrue(report.items().stream().anyMatch(i -> i.alertId().equals("ALR-00001")), report.items().toString());
        }
    }
}
