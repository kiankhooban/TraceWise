package tracewise.actions;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tracewise.audit.AuditFilter;
import tracewise.cases.state.CaseStatus;
import tracewise.domain.Actor;
import tracewise.domain.ActorType;
import tracewise.domain.AlertStatus;
import tracewise.domain.FollowUpTask;

class CommandDispatcherTest {

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

    @Test
    void lowRiskAgentActionsRunAtOnceAndAreAudited() {
        var result = f.dispatcher.submit(new SetPriorityCommand("ALR-00001", 1), Actor.AGENT, "Large pass-through");
        assertEquals(DispatchResult.Status.EXECUTED, result.status());
        assertEquals(1, f.alerts.findById("ALR-00001").orElseThrow().priority());

        f.dispatcher.submit(new AddToWatchlistCommand("C3"), Actor.AGENT, "Funds the flagged account");
        assertTrue(f.accounts.findAccount("C3").orElseThrow().watchlisted());

        f.dispatcher.submit(new CreateTaskCommand("CAS-00001", "Call the branch about C1"), Actor.AGENT, "Need KYC");
        assertEquals(List.of(new FollowUpTask("Call the branch about C1", false)),
                f.cases.findById("CAS-00001").orElseThrow().tasks());

        var executed = f.audit.query(new AuditFilter("ACTION_EXECUTED", null));
        assertEquals(3, executed.size());
        assertEquals(ActorType.AGENT, executed.get(0).actor().type());
        assertTrue(executed.get(0).details().contains("AUTONOMOUS") && executed.get(0).details().contains("Large pass-through"),
                executed.get(0).details());
    }

    @Test
    void invalidRequestsAreRejectedWithTheReasonAndAudited() {
        var unknown = f.dispatcher.submit(new SetPriorityCommand("ALR-99999", 1), Actor.AGENT, "x");
        assertEquals(DispatchResult.Status.REJECTED, unknown.status());
        assertTrue(unknown.message().contains("ALR-99999"), unknown.message());
        assertEquals(DispatchResult.Status.REJECTED,
                f.dispatcher.submit(new SetPriorityCommand("ALR-00001", 7), Actor.AGENT, "x").status());
        assertEquals(DispatchResult.Status.REJECTED,
                f.dispatcher.submit(new AddToWatchlistCommand("EXT-9"), Actor.AGENT, "x").status());
        assertEquals(2, f.alerts.findById("ALR-00001").orElseThrow().priority());
        assertEquals(3, f.audit.query(new AuditFilter("ACTION_REJECTED", null)).size());
    }

    @Test
    void thePolicyNotTheAgentDecidesWhatNeedsApproval() {
        var policy = new PermissionPolicy();
        assertEquals(PermissionTier.REQUIRES_APPROVAL, policy.tierFor(new CloseCaseCommand("CAS-00001"), Actor.AGENT));
        assertEquals(PermissionTier.REQUIRES_APPROVAL, policy.tierFor(new EscalateCaseCommand("CAS-00001"), Actor.AGENT));
        assertEquals(PermissionTier.AUTONOMOUS, policy.tierFor(new SetPriorityCommand("ALR-00001", 1), Actor.AGENT));
        assertEquals(PermissionTier.AUTONOMOUS, policy.tierFor(new CloseCaseCommand("CAS-00001"), analyst));
    }

    @Test
    void anAgentCloseIsOnlyProposedAndTheCaseWaitsForApproval() {
        var result = f.dispatcher.submit(new CloseCaseCommand("CAS-00001"), Actor.AGENT, "Payroll pattern");
        assertEquals(DispatchResult.Status.PROPOSED, result.status());
        assertEquals("APR-00001", result.pendingId());
        assertEquals(CaseStatus.PENDING_APPROVAL, f.caseStatus());
        assertEquals(AlertStatus.IN_CASE, f.alerts.findById("ALR-00001").orElseThrow().status());
        assertEquals(1, f.audit.query(new AuditFilter("ACTION_PROPOSED", "CAS-00001")).size());

        var again = f.dispatcher.submit(new EscalateCaseCommand("CAS-00001"), Actor.AGENT, "Changed my mind");
        assertEquals(DispatchResult.Status.REJECTED, again.status());
        assertTrue(again.message().contains("PENDING_APPROVAL"), again.message());
    }

    @Test
    void linkingAddsOtherAlertsToTheCase() {
        assertEquals(DispatchResult.Status.EXECUTED,
                f.dispatcher.submit(new LinkAlertsCommand("CAS-00001", List.of("ALR-00002")), Actor.AGENT, "Same funds").status());
        assertEquals(List.of("ALR-00001", "ALR-00002"), f.cases.findById("CAS-00001").orElseThrow().alertIds());
        assertEquals(AlertStatus.IN_CASE, f.alerts.findById("ALR-00002").orElseThrow().status());
        assertEquals(DispatchResult.Status.REJECTED,
                f.dispatcher.submit(new LinkAlertsCommand("CAS-00001", List.of("ALR-00009")), Actor.AGENT, "x").status());
    }

    @Test
    void nothingCanBeLinkedIntoACaseWaitingForApproval() {
        f.dispatcher.submit(new CloseCaseCommand("CAS-00001"), Actor.AGENT, "Payroll pattern");
        var link = f.dispatcher.submit(new LinkAlertsCommand("CAS-00001", List.of("ALR-00002")), Actor.AGENT, "Same funds");
        assertEquals(DispatchResult.Status.REJECTED, link.status());
        assertTrue(link.message().contains("waiting for approval"), link.message());
        assertEquals(List.of("ALR-00001"), f.cases.findById("CAS-00001").orElseThrow().alertIds());
    }

    @Test
    void anyActionTheAgentMayTakeAloneIsListedAndEverythingElseNeedsApproval() {
        ActionCommand unknown = new ActionCommand() {
            public void validate(CommandContext ctx) { }
            public void execute(CommandContext ctx) { }
            public String describe() { return "something new"; }
            public java.util.Optional<String> targetCaseId() { return java.util.Optional.empty(); }
            public String target() { return "x"; }
        };
        assertEquals(PermissionTier.REQUIRES_APPROVAL, new PermissionPolicy().tierFor(unknown, Actor.AGENT));
        assertEquals(PermissionTier.AUTONOMOUS, new PermissionPolicy().tierFor(unknown, analyst));
    }
}
