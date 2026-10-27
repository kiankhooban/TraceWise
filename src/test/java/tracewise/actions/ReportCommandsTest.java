package tracewise.actions;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tracewise.agent.AgentFinding;
import tracewise.agent.Claim;
import tracewise.agent.Verdict;
import tracewise.audit.AuditFilter;
import tracewise.cases.state.CaseStatus;
import tracewise.domain.Actor;
import tracewise.domain.ActorType;
import tracewise.domain.Case;
import tracewise.reporting.ReportStatus;
import tracewise.reporting.StrReport;
import tracewise.verification.ClaimCheck;
import tracewise.verification.VerificationReport;
import tracewise.verification.VerificationStatus;

class ReportCommandsTest {

    static final Actor ANALYST = new Actor("kian", ActorType.ANALYST);

    @TempDir Path dir;
    ActionsFixture fx;

    @BeforeEach
    void open() {
        fx = new ActionsFixture(dir);
    }

    @AfterEach
    void close() {
        fx.close();
    }

    /** Puts CAS-00001 in the given state with a verified finding citing t1, and a complete draft. */
    StrReport caseWithDraft(CaseStatus status) {
        var claim = new Claim("t1", "C1", new BigDecimal("100"), LocalDate.of(2026, 9, 1));
        var finding = new AgentFinding(Verdict.ESCALATE, 0.9, List.of(), List.of(claim), List.of(), "n", false,
                new tracewise.agent.AgentTrace("ALR-00001", tracewise.llm.ModelConfig.of("replay", "claude-haiku-5-5", false),
                        BigDecimal.ONE));
        finding.attachVerification(new VerificationReport(VerificationStatus.VERIFIED,
                List.of(new ClaimCheck(claim.describe(), true, ""))));
        var c = new Case("CAS-00001", Instant.parse("2026-09-01T00:00:00Z"), List.of("ALR-00001"), status,
                List.of(finding));
        var draft = StrReport.draft("CAS-00001", "C1", List.of(fx.accounts.findTransaction("t1").orElseThrow()),
                "Grounds", "Actions");
        c.saveReport(draft);
        fx.cases.save(c);
        return draft;
    }

    StrReport stored() {
        return fx.cases.findById("CAS-00001").orElseThrow().report().orElseThrow();
    }

    @Test
    void theAgentMaySaveADraftOnItsOwnAndItIsAudited() {
        var draft = StrReport.draft("CAS-00001", "C1", List.of(), "Grounds", "Actions");

        var r = fx.dispatcher.submit(new SaveReportDraftCommand(draft), Actor.AGENT, "drafted from the finding");

        assertEquals(DispatchResult.Status.EXECUTED, r.status());
        assertEquals("Grounds", stored().groundsForSuspicion());
        assertTrue(fx.audit.query(new AuditFilter("ACTION_EXECUTED", "CAS-00001")).get(0).details()
                .contains("Save the report draft for case CAS-00001"));
    }

    @Test
    void aDraftCannotBeSavedOverAFinalReportOrOnAClosedCase() {
        caseWithDraft(CaseStatus.ESCALATED);
        var c = fx.cases.findById("CAS-00001").orElseThrow();
        c.report().orElseThrow().lock(Instant.now());
        fx.cases.save(c);

        var r = fx.dispatcher.submit(new SaveReportDraftCommand(StrReport.draft("CAS-00001", "C1", List.of(), "x", "y")),
                ANALYST, "edit");
        assertEquals(DispatchResult.Status.REJECTED, r.status());
        assertTrue(r.message().contains("final"), r.message());

        fx.cases.save(new Case("CAS-00002", Instant.now(), List.of("ALR-00002"), CaseStatus.CLOSED));
        var closed = fx.dispatcher.submit(new SaveReportDraftCommand(StrReport.draft("CAS-00002", "C3", List.of(), "x",
                "y")), ANALYST, "edit");
        assertEquals(DispatchResult.Status.REJECTED, closed.status());
        assertTrue(closed.message().contains("CLOSED"), closed.message());
    }

    @Test
    void finalisationAlwaysWaitsForApprovalEvenWhenAnAnalystAsks() {
        var draft = caseWithDraft(CaseStatus.ESCALATED);

        var r = fx.dispatcher.submit(FinaliseReportCommand.of(draft), ANALYST, "report complete");

        assertEquals(DispatchResult.Status.PROPOSED, r.status());
        assertEquals(CaseStatus.ESCALATED, fx.caseStatus());
        assertEquals(ReportStatus.DRAFT, stored().status());

        fx.queue.approve(r.pendingId(), new Actor("sam", ActorType.SUPERVISOR));

        assertEquals(ReportStatus.FINAL, stored().status());
        assertNotNull(stored().finalisedAt());
        assertEquals(CaseStatus.ESCALATED, fx.caseStatus());
        assertEquals(ProposalStatus.APPROVED, fx.queue.find(r.pendingId()).status());
    }

    @Test
    void aReportCanBeFinalisedOnlyOnceTheCaseIsEscalated() {
        var draft = caseWithDraft(CaseStatus.UNDER_INVESTIGATION);

        var r = fx.dispatcher.submit(FinaliseReportCommand.of(draft), ANALYST, "report complete");

        assertEquals(DispatchResult.Status.REJECTED, r.status());
        assertTrue(r.message().contains("escalat"), r.message());
        assertEquals(CaseStatus.UNDER_INVESTIGATION, fx.caseStatus());
    }

    @Test
    void finalisationIsRefusedWithEveryProblemListed() {
        caseWithDraft(CaseStatus.ESCALATED);
        var c = fx.cases.findById("CAS-00001").orElseThrow();
        var incomplete = c.report().orElseThrow().withText(" ", "");
        c.saveReport(incomplete);
        fx.cases.save(c);

        var r = fx.dispatcher.submit(FinaliseReportCommand.of(incomplete), ANALYST, "report complete");

        assertEquals(DispatchResult.Status.REJECTED, r.status());
        assertTrue(r.message().contains("Missing: grounds for suspicion") && r.message().contains("Missing: actions taken"),
                r.message());
    }

    @Test
    void editingTheDraftAfterTheRequestExpiresTheProposal() {
        var draft = caseWithDraft(CaseStatus.ESCALATED);
        var r = fx.dispatcher.submit(FinaliseReportCommand.of(draft), ANALYST, "report complete");
        fx.dispatcher.submit(new SaveReportDraftCommand(draft.withText("Changed grounds", "Actions")), ANALYST, "edit");

        fx.queue.approve(r.pendingId(), new Actor("sam", ActorType.SUPERVISOR));

        assertEquals(ProposalStatus.EXPIRED, fx.queue.find(r.pendingId()).status());
        assertEquals(ReportStatus.DRAFT, stored().status());
        assertEquals("Changed grounds", stored().groundsForSuspicion());
    }

    @Test
    void aRejectedFinalisationLeavesTheDraftAndTheCaseUnchanged() {
        var draft = caseWithDraft(CaseStatus.ESCALATED);
        var r = fx.dispatcher.submit(FinaliseReportCommand.of(draft), ANALYST, "report complete");

        fx.queue.reject(r.pendingId(), new Actor("sam", ActorType.SUPERVISOR), "Name the counterparties");

        assertEquals(ReportStatus.DRAFT, stored().status());
        assertEquals(CaseStatus.ESCALATED, fx.caseStatus());
    }
}
