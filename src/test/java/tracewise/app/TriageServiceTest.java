package tracewise.app;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tracewise.agent.AgentEventListener;
import tracewise.agent.AgentOutcome;
import tracewise.agent.AgentRunnerTest;
import tracewise.agent.AgentRunnerTest.ScriptedModel;
import tracewise.agent.TraceStep;
import tracewise.agent.TriageReport;
import tracewise.audit.AuditFilter;
import tracewise.audit.AuditLog;
import tracewise.audit.SqliteAuditRepository;
import tracewise.domain.ActorType;
import tracewise.domain.AlertStatus;
import tracewise.llm.LLMException;
import tracewise.llm.ModelConfig;
import tracewise.monitoring.AlertFilter;
import tracewise.persistence.Database;
import tracewise.tools.ScenarioGenerator;

class TriageServiceTest {

    @TempDir Path dir;
    final ScriptedModel model = new ScriptedModel();

    // risk order of the sample alerts: ALR-00002 (65), ALR-00001 (60), ALR-00004 (46), ALR-00003 (43)
    static String answer(String verdict) {
        return "{\"verdict\":\"" + verdict + "\",\"confidence\":0.7,\"indicators\":[],\"cited_transactions\":[],"
                + "\"totals\":[],\"narrative\":\"n\"}";
    }

    static String forCase(String caseId) {
        return "{\"case_id\":\"" + caseId + "\",\"justification\":\"j\"}";
    }

    TraceWiseFacade facade() {
        var config = new AppConfig(dir.resolve("t.db"), "kian", ActorType.ANALYST,
                ModelConfig.of("anthropic", "claude-haiku-5-5", false), dir.resolve("replays"), dir.resolve(".env"));
        return TraceWiseBootstrap.create(config, () -> model);
    }

    @BeforeEach
    void setUp() throws Exception {
        try (var f = facade()) {
            f.importTransactions(Files.write(dir.resolve("s.csv"), ScenarioGenerator.generate(42)));
            f.runMonitoring();
        }
    }

    TriageReport triage(TriageLimits limits, AgentEventListener listener) {
        try (var f = facade()) {
            return f.triage(limits, listener);
        }
    }

    @Test
    void investigatesOpenAlertsInRiskOrderAndSummarises() {
        model.then(AgentRunnerTest.toolCall("propose_escalation", forCase("CAS-00002")))
                .then(AgentRunnerTest.answer(answer("ESCALATE")))
                .then(AgentRunnerTest.toolCall("propose_close", forCase("CAS-00001")))
                .then(AgentRunnerTest.answer(answer("DISMISS")))
                .then(new LLMException("overloaded", true, null))
                .then(AgentRunnerTest.answer(answer("INCONCLUSIVE")));
        var report = triage(TriageLimits.DEFAULT, null);

        assertEquals(TriageReport.EndReason.COMPLETED, report.endReason());
        assertEquals(List.of("ALR-00002", "ALR-00001", "ALR-00004", "ALR-00003"),
                report.items().stream().map(TriageReport.Item::alertId).toList());
        assertEquals(4, report.processed());
        assertEquals(1, report.failed());
        assertTrue(report.failures().get(0).contains("ALR-00004") && report.failures().get(0).contains("overloaded"),
                report.failures().toString());
        assertEquals(1, report.escalationsProposed());
        assertEquals(1, report.closesProposed());
        assertTrue(report.costCad().signum() > 0);
        assertTrue(report.summary().startsWith("Processed 4 alerts"), report.summary());

        try (var db = Database.open(dir.resolve("t.db"))) {
            var finished = new AuditLog(new SqliteAuditRepository(db)).query(new AuditFilter("TRIAGE_FINISHED", null));
            assertEquals(1, finished.size());
        }
    }

    @Test
    void aSecondTriageRetriesOnlyTheAlertThatFailed() {
        model.then(AgentRunnerTest.answer(answer("ESCALATE"))).then(AgentRunnerTest.answer(answer("ESCALATE")))
                .then(new LLMException("overloaded", true, null)).then(AgentRunnerTest.answer(answer("DISMISS")));
        triage(TriageLimits.DEFAULT, null);

        model.then(AgentRunnerTest.answer(answer("INCONCLUSIVE")));
        var again = triage(TriageLimits.DEFAULT, null);
        assertEquals(List.of("ALR-00004"), again.items().stream().map(TriageReport.Item::alertId).toList());
        assertEquals(0, again.failed());

        var nothing = triage(TriageLimits.DEFAULT, null);
        assertEquals(TriageReport.EndReason.NOTHING_TO_TRIAGE, nothing.endReason());
        assertEquals("Nothing to triage", nothing.summary());
    }

    @Test
    void theAlertLimitLeavesTheRestOpen() {
        model.then(AgentRunnerTest.answer(answer("ESCALATE"))).then(AgentRunnerTest.answer(answer("ESCALATE")));
        var report = triage(new TriageLimits(2, new BigDecimal("5.00"), AlertFilter.ALL), null);
        assertEquals(TriageReport.EndReason.ALERT_LIMIT, report.endReason());
        assertEquals(2, report.processed());
        try (var f = facade()) {
            assertEquals(2, f.listAlerts(new AlertFilter(null, AlertStatus.NEW, null)).size());
        }
    }

    @Test
    void theCostLimitStopsBeforeTheNextAlert() {
        model.then(AgentRunnerTest.answer(answer("ESCALATE")));
        var report = triage(new TriageLimits(50, new BigDecimal("0.0001"), AlertFilter.ALL), null);
        assertEquals(TriageReport.EndReason.COST_LIMIT, report.endReason());
        assertEquals(1, report.processed());
    }

    @Test
    void stopEndsTriageAfterTheCurrentAlert() {
        model.then(AgentRunnerTest.answer(answer("ESCALATE"))).then(AgentRunnerTest.answer(answer("ESCALATE")));
        try (var f = facade()) {
            var report = f.triage(TriageLimits.DEFAULT, new AgentEventListener() {
                @Override
                public void onStep(TraceStep step) {
                }

                @Override
                public void onFinished(AgentOutcome outcome) {
                    f.stopTriage();
                }
            });
            assertEquals(TriageReport.EndReason.STOPPED, report.endReason());
            assertEquals(1, report.processed());
        }
    }

    @Test
    void theFilterSelectsWhichAlertsToTriage() {
        model.then(AgentRunnerTest.answer(answer("ESCALATE")));
        var report = triage(new TriageLimits(50, new BigDecimal("5.00"), new AlertFilter("R5", null, null)), null);
        assertEquals(List.of("ALR-00003"), report.items().stream().map(TriageReport.Item::alertId).toList());
    }
}
