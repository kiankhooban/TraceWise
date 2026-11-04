package tracewise.ui.gui;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import tracewise.actions.CloseCaseCommand;
import tracewise.actions.EscalateCaseCommand;
import tracewise.actions.PendingAction;
import tracewise.agent.AgentFinding;
import tracewise.agent.Verdict;
import tracewise.cases.state.CaseStatus;
import tracewise.domain.Actor;
import tracewise.domain.Case;
import tracewise.verification.ClaimCheck;
import tracewise.verification.VerificationReport;
import tracewise.verification.VerificationStatus;

/** One row of the approval queue (docs/stage2/ui-design.md: agent proposes, human decides, every state in words). */
class ProposalRowTest {

    static final Instant PROPOSED = Instant.parse("2026-11-03T10:00:00Z");

    static Case caseWith(String id, Verdict verdict, VerificationStatus status, boolean... confirmed) {
        var checks = new java.util.ArrayList<ClaimCheck>();
        for (boolean ok : confirmed) {
            checks.add(new ClaimCheck("claim", ok, ok ? "" : "no such transaction"));
        }
        var f = new AgentFinding(verdict, 0.7, List.of(), List.of(), List.of(), "narrative", false, null);
        f.attachVerification(new VerificationReport(status, checks));
        return new Case(id, PROPOSED, List.of("ALR-00002"), CaseStatus.PENDING_APPROVAL, List.of(f));
    }

    @Test
    void anEscalationReadsAsTheAgentsProposalWithItsCheckedFinding() {
        var p = new PendingAction("APR-00001", new EscalateCaseCommand("CAS-00002"), "why", Actor.AGENT, PROPOSED);
        var row = new ProposalRow(p, caseWith("CAS-00002", Verdict.ESCALATE, VerificationStatus.VERIFIED,
                true, true, true, true, true, true), "C1059", PROPOSED.plusSeconds(12 * 60));

        assertEquals("ESCALATE 0.7", row.finding());
        assertEquals("verdict-ESCALATE", row.findingStyle());
        assertEquals("VER 6/6", row.checked());
        assertEquals("ok", row.checkedStyle());
        assertEquals("AGENT", row.proposedBy());
        assertEquals("12m", row.waiting());
        assertEquals("CAS-00002 becomes Escalated", row.ifApproved());
    }

    @Test
    void aCloseProposalSaysItsAlertsCloseToo() {
        var p = new PendingAction("APR-00002", new CloseCaseCommand("CAS-00001"), "why", Actor.AGENT, PROPOSED);
        var row = new ProposalRow(p, caseWith("CAS-00001", Verdict.DISMISS, VerificationStatus.VERIFIED, true), "C1037", PROPOSED);
        assertEquals("CAS-00001 becomes Closed and its alerts close", row.ifApproved());
    }

    @Test
    void aRejectedClaimShowsTheFindingAsUnverified() {
        var p = new PendingAction("APR-00003", new EscalateCaseCommand("CAS-00004"), "why", Actor.AGENT, PROPOSED);
        var row = new ProposalRow(p, caseWith("CAS-00004", Verdict.ESCALATE, VerificationStatus.PARTIALLY_VERIFIED,
                true, false), "C1066", PROPOSED);
        assertEquals("UNVER 1/2", row.checked());
        assertEquals("bad", row.checkedStyle());
    }

    @Test
    void withoutAFindingTheFindingCellsAreEmpty() {
        var p = new PendingAction("APR-00004", new EscalateCaseCommand("CAS-00009"), "why", Actor.AGENT, PROPOSED);
        var row = new ProposalRow(p, new Case("CAS-00009", PROPOSED, List.of("ALR-00009")), "", PROPOSED);
        assertEquals("", row.finding());
        assertNull(row.findingStyle());
        assertEquals("", row.checked());
        assertNull(row.checkedStyle());
    }

    @Test
    void aReloadKeepsTheProposalTheAnalystWasOn() {
        var a = new ProposalRow(new PendingAction("APR-00001", new EscalateCaseCommand("CAS-1"), "", Actor.AGENT, PROPOSED), null, "", PROPOSED);
        var b = new ProposalRow(new PendingAction("APR-00002", new EscalateCaseCommand("CAS-2"), "", Actor.AGENT, PROPOSED), null, "", PROPOSED);
        var rows = List.of(a, b);
        assertEquals(b, ProposalRow.pick(rows, null, "APR-00002").orElseThrow(), "the one the analyst was on");
        assertEquals(a, ProposalRow.pick(rows, "APR-00001", "APR-00002").orElseThrow(), "an id typed in the command bar wins");
        assertEquals(a, ProposalRow.pick(rows, null, "APR-00009").orElseThrow(), "decided meanwhile: the first one");
        assertTrue(ProposalRow.pick(List.of(), null, null).isEmpty());
    }
}
