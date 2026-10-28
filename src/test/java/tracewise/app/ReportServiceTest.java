package tracewise.app;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tracewise.agent.AgentRunnerTest;
import tracewise.agent.AgentRunnerTest.ScriptedModel;
import tracewise.audit.AuditFilter;
import tracewise.domain.ActorType;
import tracewise.domain.Transaction;
import tracewise.llm.LLMException;
import tracewise.llm.LLMMessage;
import tracewise.llm.ModelConfig;
import tracewise.persistence.Database;
import tracewise.reporting.ExportFormat;
import tracewise.reporting.ReportStatus;

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

    @BeforeEach
    void anEscalatedCase() throws Exception {
        try (var f = facade()) {
            var setup = EscalatedCase.create(f, dir, dir.resolve("t.db"), model);
            subject = setup.subject();
            cited = setup.cited();
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

            var r = f.draftReport("CAS-00001", false);

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
            assertTrue(prompt.contains("not verified"), "the agent's own summary is marked unverified (finding 4): " + prompt);
            assertTrue(request.systemPrompt().contains("summary"), request.systemPrompt());
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
            f.draftReport("CAS-00001", false);
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
            f.draftReport("CAS-00001", false);

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
            f.draftReport("CAS-00001", false);
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

            assertThrows(IllegalStateException.class, () -> f.draftReport("CAS-00001", false));

            assertTrue(f.getReport("CAS-00001").isEmpty());
            assertThrows(IllegalArgumentException.class, () -> f.draftReport("CAS-99999", false));
        }
    }

    /** Finding 1: an edit after the request expires the waiting request, so the edited draft can be requested. */
    @Test
    void editingAfterARequestExpiresItAndTheEditedDraftCanBeRequested() {
        try (var f = facade()) {
            narrative("Deposits " + cited.id() + ".");
            f.draftReport("CAS-00001", false);
            var first = f.requestFinalisation("CAS-00001");
            f.saveReport("CAS-00001", "Edited deposits " + cited.id() + ".", f.getReport("CAS-00001").orElseThrow()
                    .actionsTaken());

            assertEquals(tracewise.actions.ProposalStatus.EXPIRED, f.pendingApprovals().stream()
                    .filter(p -> p.id().equals(first.id())).findFirst().map(p -> p.status())
                    .orElse(tracewise.actions.ProposalStatus.EXPIRED));
            var second = f.requestFinalisation("CAS-00001");
            assertNotEquals(first.id(), second.id());
            assertEquals(List.of(second.id()), f.pendingApprovals().stream().map(p -> p.id()).toList());
        }
    }

    /** Finding 8: drafting again replaces the analyst's edits only when asked to. */
    @Test
    void anExistingDraftIsReplacedOnlyWhenAsked() {
        try (var f = facade()) {
            narrative("First " + cited.id() + ".");
            f.draftReport("CAS-00001", false);
            f.saveReport("CAS-00001", "My edits", "Mine");
            int calls = model.requests.size();

            var e = assertThrows(IllegalStateException.class, () -> f.draftReport("CAS-00001", false));
            assertTrue(e.getMessage().contains("already has a draft"), e.getMessage());
            assertEquals(calls, model.requests.size());
            assertEquals("My edits", f.getReport("CAS-00001").orElseThrow().groundsForSuspicion());

            narrative("Second " + cited.id() + ".");
            assertEquals("Second " + cited.id() + ".", f.draftReport("CAS-00001", true).groundsForSuspicion());
        }
    }

    /** Finding 13: nothing is paid for when the case cannot have a report drafted. */
    @Test
    void draftingIsRefusedBeforeAnyModelCallWhenTheCaseCannotTakeADraft() throws Exception {
        try (var db = Database.open(dir.resolve("t.db"))) {
            var cases = new tracewise.persistence.SqliteCaseRepository(db);
            cases.save(new tracewise.domain.Case("CAS-00002", java.time.Instant.now(), List.of("ALR-00002"),
                    tracewise.cases.state.CaseStatus.UNDER_INVESTIGATION));
            cases.save(new tracewise.domain.Case("CAS-00003", java.time.Instant.now(), List.of("ALR-00003"),
                    tracewise.cases.state.CaseStatus.CLOSED));
        }
        try (var f = facade()) {
            narrative("Deposits " + cited.id() + ".");
            f.draftReport("CAS-00001", false);
            f.approve(f.requestFinalisation("CAS-00001").id());
            int calls = model.requests.size();

            for (String id : List.of("CAS-00001", "CAS-00002", "CAS-00003")) {
                assertThrows(IllegalStateException.class, () -> f.draftReport(id, true), id);
            }
            assertEquals(calls, model.requests.size());
        }
    }

    /** Finding 6: a draft made before the escalation is approved does not state a status that will change. */
    @Test
    void actionsTakenDoNotStateAStatusThatWillChange() throws Exception {
        Path other = dir.resolve("pending");
        Files.createDirectories(other);
        var pendingModel = new ScriptedModel();
        try (var f = TraceWiseBootstrap.create(new AppConfig(other.resolve("t.db"), "kian", ActorType.ANALYST,
                ModelConfig.of("anthropic", "claude-haiku-5-5", false), other.resolve("replays"), other.resolve(".env")),
                () -> pendingModel)) {
            var setup = EscalatedCase.create(f, other, other.resolve("t.db"), pendingModel, false);
            pendingModel.then(AgentRunnerTest.answer("Deposits " + setup.cited().id() + "."));

            var r = f.draftReport("CAS-00001", false);

            assertFalse(r.actionsTaken().contains("status") || r.actionsTaken().contains("PENDING"), r.actionsTaken());
        }
    }

    /** Finding 14: the subject is the account of the alert that opened the case, not of the lowest linked id. */
    @Test
    void theSubjectComesFromTheAlertThatOpenedTheCase() {
        assertEquals("ALR-00002", new tracewise.domain.Case("CAS-00002", java.time.Instant.now(),
                List.of("ALR-00001", "ALR-00002")).openingAlertId());
        assertEquals("ALR-00007", new tracewise.domain.Case("CAS-00099", java.time.Instant.now(),
                List.of("ALR-00007")).openingAlertId());
    }
}
