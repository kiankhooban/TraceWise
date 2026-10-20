package tracewise.app;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tracewise.actions.ApprovalQueue;
import tracewise.actions.CommandContext;
import tracewise.actions.CommandDispatcher;
import tracewise.actions.PermissionPolicy;
import tracewise.agent.AgentEventListener;
import tracewise.agent.AgentLimits;
import tracewise.agent.AgentOutcome;
import tracewise.agent.AgentRunner;
import tracewise.agent.AgentRunnerTest;
import tracewise.agent.AgentRunnerTest.ScriptedModel;
import tracewise.agent.OutcomeStatus;
import tracewise.agent.TraceStep;
import tracewise.agent.Verdict;
import tracewise.audit.AuditFilter;
import tracewise.audit.AuditLog;
import tracewise.audit.SqliteAuditRepository;
import tracewise.cases.state.CaseStatus;
import tracewise.domain.Actor;
import tracewise.domain.ActorType;
import tracewise.domain.Alert;
import tracewise.domain.AlertStatus;
import tracewise.ingest.CurrencyConverter;
import tracewise.ingest.ScenarioCsvFormat;
import tracewise.llm.LLMException;
import tracewise.monitoring.AlertFilter;
import tracewise.persistence.Database;
import tracewise.persistence.SqliteAlertRepository;
import tracewise.persistence.SqliteApprovalRepository;
import tracewise.persistence.SqliteCaseRepository;
import tracewise.persistence.SqliteRuleConfigRepository;
import tracewise.persistence.SqliteThresholdReportRepository;
import tracewise.persistence.SqliteTransactionRepository;
import tracewise.monitoring.RiskScorer;
import tracewise.tools.ScenarioGenerator;
import tracewise.verification.ClaimVerifier;
import tracewise.verification.VerificationStatus;

class InvestigationServiceTest {

    @TempDir Path dir;
    Database db;
    SqliteTransactionRepository transactions;
    SqliteAlertRepository alerts;
    SqliteCaseRepository cases;
    AuditLog audit;
    final Actor analyst = new Actor("kian", ActorType.ANALYST);
    final ScriptedModel model = new ScriptedModel();
    Alert structuring;

    @BeforeEach
    void setUp() throws Exception {
        db = Database.open(dir.resolve("i.db"));
        transactions = new SqliteTransactionRepository(db);
        alerts = new SqliteAlertRepository(db);
        cases = new SqliteCaseRepository(db);
        audit = new AuditLog(new SqliteAuditRepository(db));
        Path file = Files.write(dir.resolve("s.csv"), ScenarioGenerator.generate(42));
        new ImportService(transactions, List.of(new ScenarioCsvFormat(CurrencyConverter.bankOfCanadaDefaults())), audit,
                analyst).importFile(file);
        new MonitoringService(transactions, MonitoringServiceTest.allRules(), alerts, new SqliteThresholdReportRepository(db),
                new SqliteRuleConfigRepository(db), new RiskScorer(), audit, analyst).run();
        structuring = alerts.find(new AlertFilter("R3", null, null)).get(0);
    }

    @AfterEach
    void tearDown() {
        db.close();
    }

    InvestigationService service() {
        return service(AgentLimits.DEFAULT);
    }

    InvestigationService service(AgentLimits limits) {
        var ctx = new CommandContext(cases, alerts, transactions);
        var queue = new ApprovalQueue(db, new SqliteApprovalRepository(db), ctx, audit);
        var tools = TraceWiseBootstrap.investigationTools(transactions, alerts, cases,
                new CommandDispatcher(db, ctx, new PermissionPolicy(), queue, audit));
        var runner = new AgentRunner(model, tools, limits);
        return new InvestigationService(db, alerts, cases, () -> runner, new ClaimVerifier(transactions), audit, analyst,
                AgentRunnerTest.PRICES, BigDecimal.ONE);
    }

    /** A finding citing the alert's first transaction correctly and one transaction that does not exist. */
    String finding() {
        var t = transactions.findTransaction(structuring.transactionIds().get(0)).orElseThrow();
        return """
                ```json
                {"verdict": "ESCALATE", "confidence": 0.9, "indicators": ["Structuring"],
                 "cited_transactions": [
                   {"transaction_id": "%s", "account_id": "%s", "amount_cad": %s, "date": "%s"},
                   {"transaction_id": "invented-1", "account_id": "%s", "amount_cad": 9900, "date": "2026-09-01"}],
                 "totals": [], "narrative": "Repeated deposits under the threshold."}
                ```""".formatted(t.id(), structuring.accountId(), t.amountCad().toPlainString(),
                LocalDate.ofInstant(t.timestamp(), ZoneOffset.UTC), structuring.accountId());
    }

    static class Steps implements AgentEventListener {
        final List<TraceStep> steps = new ArrayList<>();
        AgentOutcome outcome;

        @Override
        public void onStep(TraceStep step) {
            steps.add(step);
        }

        @Override
        public void onFinished(AgentOutcome outcome) {
            this.outcome = outcome;
        }
    }

    @Test
    void investigationProducesAVerifiedFindingOnTheAlertsCase() {
        model.then(AgentRunnerTest.toolCall("account_history", "{\"account_id\":\"" + structuring.accountId() + "\"}"))
                .then(AgentRunnerTest.answer(finding()));
        var listener = new Steps();
        var result = service().investigate(structuring.id(), listener);

        assertEquals(OutcomeStatus.COMPLETED, result.status());
        assertEquals("CAS-" + structuring.id().substring(4), result.caseId());
        assertEquals(Verdict.ESCALATE, result.finding().verdict());
        var report = result.finding().verification();
        assertEquals(VerificationStatus.PARTIALLY_VERIFIED, report.status());
        assertEquals(1, report.rejected().size());
        assertEquals(1, listener.steps.size());
        assertEquals(OutcomeStatus.COMPLETED, listener.outcome.status());

        var stored = cases.findByAlert(structuring.id()).orElseThrow();
        assertEquals(CaseStatus.UNDER_INVESTIGATION, stored.status());
        assertEquals(1, stored.findings().size());
        assertEquals(AlertStatus.IN_CASE, alerts.findById(structuring.id()).orElseThrow().status());
        assertEquals(1, audit.query(new AuditFilter("AGENT_STEP", structuring.id())).size());
        assertEquals(1, audit.query(new AuditFilter("INVESTIGATION_STARTED", structuring.id())).size());
    }

    @Test
    void failedModelLeavesTheAlertOpenForAnotherTry() {
        model.then(new LLMException("overloaded", true, null));
        var failed = service().investigate(structuring.id(), null);
        assertEquals(OutcomeStatus.FAILED, failed.status());
        assertNull(failed.finding());
        assertTrue(failed.message().contains("overloaded"));
        assertEquals(0, cases.findByAlert(structuring.id()).orElseThrow().findings().size());

        model.then(AgentRunnerTest.answer(finding()));
        var retried = service().investigate(structuring.id(), null);
        assertEquals(OutcomeStatus.COMPLETED, retried.status());
        assertEquals(failed.caseId(), retried.caseId());
    }

    @Test
    void unknownAlertIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> service().investigate("ALR-99999", null));
    }

    String historyCall() {
        return "{\"account_id\":\"" + structuring.accountId() + "\"}";
    }

    @Test
    void aRunCutShortByTheTurnLimitStoresAnIncompleteFinding() {
        model.then(AgentRunnerTest.toolCall("account_history", historyCall())).then(AgentRunnerTest.answer(finding()));
        var result = service(new AgentLimits(1, new BigDecimal("1"))).investigate(structuring.id(), null);

        assertEquals(OutcomeStatus.INCOMPLETE, result.status());
        assertTrue(cases.findByAlert(structuring.id()).orElseThrow().latestFinding().incomplete());
    }

    @Test
    void anUnusableAnswerAtTheLimitFailsWithoutStoringAFinding() {
        model.then(AgentRunnerTest.toolCall("account_history", historyCall())).then(AgentRunnerTest.answer("No idea."));
        var result = service(new AgentLimits(1, new BigDecimal("1"))).investigate(structuring.id(), null);

        assertEquals(OutcomeStatus.FAILED, result.status());
        assertTrue(result.message().contains("not a valid finding"), result.message());
        assertTrue(cases.findByAlert(structuring.id()).orElseThrow().findings().isEmpty());
    }

    @Test
    void anUnexpectedErrorStillEndsTheRunAsFailedAndAudited() {
        model.then(new IllegalStateException("recording folder is read-only"));
        var result = service().investigate(structuring.id(), null);

        assertEquals(OutcomeStatus.FAILED, result.status());
        assertTrue(result.message().contains("read-only"), result.message());
        var finished = audit.query(new AuditFilter("INVESTIGATION_FINISHED", structuring.id()));
        assertEquals("FAILED", finished.get(0).outcome());
    }

    @Test
    void aClosedAlertIsNotInvestigated() {
        alerts.updateStatus(structuring.id(), AlertStatus.CLOSED);
        assertThrows(IllegalStateException.class, () -> service().investigate(structuring.id(), null));
        assertTrue(model.requests.isEmpty());
    }

    @Test
    void cancellingTheRunningAlertStopsItAfterTheCurrentStep() {
        var service = service();
        model.beforeEachCall = () -> service.cancel(structuring.id());
        model.then(AgentRunnerTest.toolCall("account_history", historyCall())).then(AgentRunnerTest.answer(finding()));
        var result = service.investigate(structuring.id(), null);

        assertEquals(OutcomeStatus.CANCELLED, result.status());
        assertNull(result.finding());
        assertEquals(1, model.requests.size());
        assertEquals("CANCELLED", audit.query(new AuditFilter("INVESTIGATION_FINISHED", structuring.id())).get(0).outcome());
    }

    @Test
    void cancellingADifferentAlertLeavesTheRunGoing() {
        var service = service();
        model.beforeEachCall = () -> service.cancel("ALR-99999");
        model.then(AgentRunnerTest.toolCall("account_history", historyCall())).then(AgentRunnerTest.answer(finding()));
        assertEquals(OutcomeStatus.COMPLETED, service.investigate(structuring.id(), null).status());
    }

    @Test
    void aProposalMadeDuringTheRunIsNotUndoneWhenTheRunEnds() {
        String caseId = "CAS-" + structuring.id().substring(4);
        model.then(AgentRunnerTest.toolCall("propose_escalation",
                        "{\"case_id\":\"" + caseId + "\",\"justification\":\"Structured cash deposits\"}"))
                .then(AgentRunnerTest.answer(finding()));
        var result = service().investigate(structuring.id(), null);

        assertEquals(OutcomeStatus.COMPLETED, result.status());
        assertFalse(result.trace().steps().get(0).error(), result.trace().steps().get(0).result());
        var stored = cases.findById(caseId).orElseThrow();
        assertEquals(CaseStatus.PENDING_APPROVAL, stored.status());
        assertEquals(1, stored.findings().size());
    }
}
