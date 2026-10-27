package tracewise.app;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tracewise.agent.AgentRunnerTest;
import tracewise.agent.AgentRunnerTest.ScriptedModel;
import tracewise.audit.AuditFilter;
import tracewise.cases.state.CaseStatus;
import tracewise.domain.ActorType;
import tracewise.domain.Transaction;
import tracewise.llm.LLMException;
import tracewise.llm.LLMMessage;
import tracewise.llm.ModelConfig;
import tracewise.monitoring.AlertFilter;
import tracewise.persistence.Database;
import tracewise.persistence.SqliteTransactionRepository;
import tracewise.reporting.ExportFormat;
import tracewise.reporting.ReportStatus;
import tracewise.tools.ScenarioGenerator;

/** F11: drafting, editing, finalising through the approval queue, and exporting, through the facade. */
class ReportServiceTest {

    @TempDir Path dir;
    final ScriptedModel model = new ScriptedModel();
    Transaction cited;
    String subject;

    TraceWiseFacade facade() {
        return TraceWiseBootstrap.create(new AppConfig(dir.resolve("t.db"), "kian", ActorType.ANALYST,
                ModelConfig.of("anthropic", "claude-haiku-5-5", false), dir.resolve("replays"), dir.resolve(".env")),
                () -> model);
    }

    /** CAS-00001 is escalated with a finding that cites one real transaction and one invented one. */
    @BeforeEach
    void anEscalatedCase() throws Exception {
        try (var f = facade()) {
            f.importTransactions(Files.write(dir.resolve("s.csv"), ScenarioGenerator.generate(42)));
            f.runMonitoring();
            var alert = f.listAlerts(AlertFilter.ALL).stream().filter(a -> a.id().equals("ALR-00001")).findFirst()
                    .orElseThrow();
            subject = alert.accountId();
            try (var db = Database.open(dir.resolve("t.db"))) {
                cited = new SqliteTransactionRepository(db).findTransaction(alert.transactionIds().get(0)).orElseThrow();
            }
            model.then(AgentRunnerTest.toolCall("propose_escalation",
                    "{\"case_id\":\"CAS-00001\",\"justification\":\"Cash deposits under the threshold\"}"))
                    .then(AgentRunnerTest.answer("""
                            ```json
                            {"verdict": "ESCALATE", "confidence": 0.9, "indicators": [],
                             "cited_transactions": [
                               {"transaction_id": "%s", "account_id": "%s", "amount_cad": %s, "date": "%s"},
                               {"transaction_id": "TX-99999", "account_id": "%s", "amount_cad": 9900, "date": "2026-09-01"}],
                             "totals": [], "narrative": "Repeated deposits under the threshold, including TX-99999."}
                            ```""".formatted(cited.id(), subject, cited.amountCad().toPlainString(),
                            LocalDate.ofInstant(cited.timestamp(), ZoneOffset.UTC), subject)));
            f.investigate("ALR-00001", null);
            f.approve(f.pendingApprovals().get(0).id());
            assertEquals(CaseStatus.ESCALATED, f.getCase("CAS-00001").status());
        }
    }

    String narrative(String text) {
        model.then(AgentRunnerTest.answer(text));
        return text;
    }

    @Test
    void theDraftHasStructuredFieldsFromTheCaseAndANarrativeFromTheModel() {
        try (var f = facade()) {
            String text = narrative("Account " + subject + " received " + cited.id() + " in cash just under the threshold.");

            var r = f.draftReport("CAS-00001");

            assertEquals(subject, r.subjectAccountId());
            assertEquals(List.of(cited.id()), r.transactions().stream().map(Transaction::id).toList());
            assertEquals(text, r.groundsForSuspicion());
            assertTrue(r.actionsTaken().contains("ALR-00001") && r.actionsTaken().contains("scalated"), r.actionsTaken());
            assertEquals(ReportStatus.DRAFT, f.getReport("CAS-00001").orElseThrow().status());

            var request = model.requests.get(model.requests.size() - 1);
            assertTrue(request.tools().isEmpty());
            String prompt = ((LLMMessage.User) request.messages().get(0)).text();
            assertTrue(prompt.contains(cited.id()), prompt);
            assertTrue(prompt.contains("Rejected claims") && prompt.contains("TX-99999"), prompt);
            assertFalse(prompt.toLowerCase().contains("label"), prompt);
            var saved = f.queryAudit(new AuditFilter("ACTION_EXECUTED", "CAS-00001")).stream()
                    .filter(e -> e.details().contains("Save the report draft")).toList();
            assertEquals(ActorType.AGENT, saved.get(0).actor().type());
        }
    }

    @Test
    void anEditedDraftIsFinalisedOnlyAfterApprovalAndThenExported() throws Exception {
        try (var f = facade()) {
            narrative("Draft narrative about " + cited.id() + ".");
            f.draftReport("CAS-00001");
            f.saveReport("CAS-00001", "Edited grounds citing " + cited.id() + ".", "Edited actions.");
            assertThrows(IllegalStateException.class, () -> f.exportReport("CAS-00001", EnumSet.allOf(ExportFormat.class),
                    dir.resolve("out")));

            var pending = f.requestFinalisation("CAS-00001");
            assertEquals(ReportStatus.DRAFT, f.getReport("CAS-00001").orElseThrow().status());
            assertThrows(IllegalStateException.class, () -> f.requestFinalisation("CAS-00001"));
            f.approve(pending.id());

            var r = f.getReport("CAS-00001").orElseThrow();
            assertEquals(ReportStatus.FINAL, r.status());
            assertEquals("Edited grounds citing " + cited.id() + ".", r.groundsForSuspicion());
            assertThrows(IllegalStateException.class, () -> f.saveReport("CAS-00001", "late", "late"));

            List<Path> files = f.exportReport("CAS-00001", EnumSet.allOf(ExportFormat.class), dir.resolve("out"));
            assertEquals(List.of(dir.resolve("out/CAS-00001-STR.html"), dir.resolve("out/CAS-00001-STR.json")), files);
            assertTrue(Files.readString(files.get(1)).contains("Edited grounds"));
            var exported = f.queryAudit(new AuditFilter("REPORT_EXPORTED", "CAS-00001"));
            assertEquals(1, exported.size());
            assertTrue(exported.get(0).details().contains("CAS-00001-STR.json"));
        }
    }

    @Test
    void aRejectedClaimInTheNarrativeBlocksFinalisationUntilRemoved() {
        try (var f = facade()) {
            narrative("Deposits " + cited.id() + " and TX-99999.");
            f.draftReport("CAS-00001");

            var e = assertThrows(IllegalStateException.class, () -> f.requestFinalisation("CAS-00001"));
            assertTrue(e.getMessage().contains("TX-99999"), e.getMessage());
            assertTrue(f.pendingApprovals().isEmpty());

            f.saveReport("CAS-00001", "Deposits " + cited.id() + ".", f.getReport("CAS-00001").orElseThrow().actionsTaken());
            assertNotNull(f.requestFinalisation("CAS-00001"));
        }
    }

    @Test
    void aFailedExportKeepsTheReportFinalSoItCanBeRetried() throws Exception {
        try (var f = facade()) {
            narrative("Deposits " + cited.id() + ".");
            f.draftReport("CAS-00001");
            f.approve(f.requestFinalisation("CAS-00001").id());
            Path notADirectory = Files.writeString(dir.resolve("file"), "x");

            assertThrows(IllegalStateException.class,
                    () -> f.exportReport("CAS-00001", EnumSet.of(ExportFormat.HTML), notADirectory));

            assertEquals(ReportStatus.FINAL, f.getReport("CAS-00001").orElseThrow().status());
            assertEquals(1, f.exportReport("CAS-00001", EnumSet.of(ExportFormat.HTML), dir.resolve("out")).size());
        }
    }

    @Test
    void aFailedModelCallSavesNothing() {
        try (var f = facade()) {
            model.then(new LLMException("overloaded", false, null));

            assertThrows(IllegalStateException.class, () -> f.draftReport("CAS-00001"));

            assertTrue(f.getReport("CAS-00001").isEmpty());
            assertThrows(IllegalArgumentException.class, () -> f.draftReport("CAS-99999"));
        }
    }
}
