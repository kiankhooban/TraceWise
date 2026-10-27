package tracewise.actions;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tracewise.agent.AgentFinding;
import tracewise.agent.AgentOutcome;
import tracewise.agent.AgentTrace;
import tracewise.agent.OutcomeStatus;
import tracewise.agent.Verdict;
import tracewise.audit.AuditFilter;
import tracewise.cases.state.CaseStatus;
import tracewise.domain.Actor;
import tracewise.domain.ActorType;
import tracewise.domain.AlertStatus;
import tracewise.llm.ModelConfig;
import tracewise.persistence.SqliteApprovalRepository;

class ApprovalQueueTest {

    @TempDir Path dir;
    ActionsFixture f;
    final Actor analyst = new Actor("kian", ActorType.ANALYST);

    @BeforeEach
    void open() {
        f = new ActionsFixture(dir);
    }

    @AfterEach
    void close() {
        f.close();
    }

    String proposeClose() {
        return f.dispatcher.submit(new CloseCaseCommand("CAS-00001"), Actor.AGENT, "Payroll pattern").pendingId();
    }

    @Test
    void approvingAProposalRunsIt() {
        String id = proposeClose();
        f.queue.approve(id, analyst);

        assertEquals(CaseStatus.CLOSED, f.caseStatus());
        assertEquals(AlertStatus.CLOSED, f.alerts.findById("ALR-00001").orElseThrow().status());
        var p = f.queue.find(id);
        assertEquals(ProposalStatus.APPROVED, p.status());
        assertEquals(analyst, p.reviewer());
        assertTrue(f.queue.pending().isEmpty());
        assertEquals(1, f.audit.query(new AuditFilter("PROPOSAL_APPROVED", "CAS-00001")).size());
    }

    void giveTheCaseAFinding(String narrative) {
        var c = f.cases.findById("CAS-00001").orElseThrow();
        var trace = new AgentTrace("ALR-00001", ModelConfig.of("anthropic", "claude-haiku-5-5", false), BigDecimal.ONE);
        trace.finish(new AgentOutcome("ALR-00001", OutcomeStatus.COMPLETED, "{}"));
        c.addFinding(new AgentFinding(Verdict.ESCALATE, 0.9, List.of("Structuring"), List.of(), List.of(), narrative,
                false, trace));
        f.cases.save(c);
    }

    @Test
    void anApprovedEscalationIsRememberedAsAPastCase() {
        giveTheCaseAFinding("Cash deposits under the threshold.");
        assertEquals(List.of(), f.memory.similar("cash deposit threshold", 3));

        f.queue.approve(f.dispatcher.submit(new EscalateCaseCommand("CAS-00001"), Actor.AGENT, "Structuring")
                .pendingId(), analyst);

        assertEquals("CAS-00001", f.memory.similar("cash deposit threshold", 3).get(0).source());
    }

    @Test
    void anApprovedCloseIsRememberedAsAPastCase() {
        giveTheCaseAFinding("Regular payroll.");
        assertEquals(List.of(), f.memory.similar("payroll", 3));

        f.queue.approve(proposeClose(), analyst);

        assertEquals("CLOSED. Indicators: Structuring. Regular payroll.", f.memory.similar("payroll", 3).get(0).text());
    }

    @Test
    void rejectingNeedsAReasonAndSendsTheCaseBack() {
        String id = proposeClose();
        assertThrows(IllegalArgumentException.class, () -> f.queue.reject(id, analyst, " "));
        assertEquals(ProposalStatus.PENDING, f.queue.find(id).status());

        f.queue.reject(id, analyst, "Source of funds not checked");
        assertEquals(ProposalStatus.REJECTED, f.queue.find(id).status());
        assertEquals("Source of funds not checked", f.queue.find(id).decisionReason());
        assertEquals(CaseStatus.UNDER_INVESTIGATION, f.caseStatus());
        assertEquals(1, f.audit.query(new AuditFilter("PROPOSAL_REJECTED", "CAS-00001")).size());
    }

    @Test
    void aProposalOvertakenByEventsExpires() {
        String id = proposeClose();
        f.dispatcher.submit(new EscalateCaseCommand("CAS-00001"), analyst, "Escalating directly");
        assertEquals(CaseStatus.ESCALATED, f.caseStatus());

        f.queue.approve(id, analyst);
        assertEquals(ProposalStatus.EXPIRED, f.queue.find(id).status());
        assertEquals(CaseStatus.ESCALATED, f.caseStatus());
        assertEquals(1, f.audit.query(new AuditFilter("PROPOSAL_EXPIRED", "CAS-00001")).size());
    }

    @Test
    void theAgentCannotApproveAndDecidedProposalsStayDecided() {
        String id = proposeClose();
        assertThrows(IllegalArgumentException.class, () -> f.queue.approve(id, Actor.AGENT));
        f.queue.approve(id, analyst);
        assertThrows(IllegalStateException.class, () -> f.queue.reject(id, analyst, "too late"));
        assertThrows(IllegalArgumentException.class, () -> f.queue.approve("APR-99999", analyst));
    }

    @Test
    void proposalsSurviveARestart() {
        String id = proposeClose();
        var reopened = new ApprovalQueue(f.db, new SqliteApprovalRepository(f.db), f.ctx, f.audit);
        var p = reopened.pending().get(0);
        assertEquals(id, p.id());
        assertEquals(new CloseCaseCommand("CAS-00001"), p.command());
        assertEquals("Payroll pattern", p.justification());
        assertEquals(Actor.AGENT, p.proposedBy());
        reopened.approve(id, analyst);
        assertEquals(CaseStatus.CLOSED, f.caseStatus());
        var decided = new ApprovalQueue(f.db, new SqliteApprovalRepository(f.db), f.ctx, f.audit).find(id);
        assertEquals(analyst, decided.reviewer());
        assertNotNull(decided.decidedAt(), "an approval with no reason still has a decision time");
    }

    @Test
    void rejectingAnOvertakenProposalLeavesTheCaseAlone() {
        String id = proposeClose();
        f.dispatcher.submit(new EscalateCaseCommand("CAS-00001"), analyst, "Escalating directly");
        f.queue.reject(id, analyst, "No longer relevant");
        assertEquals(ProposalStatus.REJECTED, f.queue.find(id).status());
        assertEquals(CaseStatus.ESCALATED, f.caseStatus());
    }
}
