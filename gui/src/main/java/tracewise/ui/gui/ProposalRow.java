package tracewise.ui.gui;

import java.time.Duration;
import java.time.Instant;
import tracewise.actions.CloseCaseCommand;
import tracewise.actions.EscalateCaseCommand;
import tracewise.actions.PendingAction;
import tracewise.agent.AgentFinding;
import tracewise.domain.Case;
import tracewise.verification.VerificationReport;
import tracewise.verification.VerificationStatus;

/**
 * One row of the approval queue (F06): the proposal, the case it would change (null if it could not be loaded),
 * the subject account, and the time the queue was loaded, for the waiting time.
 */
record ProposalRow(PendingAction proposal, Case target, String account, Instant now) {

    String caseId() {
        return proposal.command().targetCaseId().orElse("");
    }

    AgentFinding latest() {
        return target == null ? null : target.latestFinding();
    }

    String finding() {
        var f = latest();
        return f == null ? "" : f.verdict() + " " + f.confidence();
    }

    String findingStyle() {
        var f = latest();
        return f == null ? null : "verdict-" + f.verdict();
    }

    private VerificationReport report() {
        var f = latest();
        return f == null ? null : f.verification();
    }

    /** "VER 6/6" when every claim matched the data, otherwise "UNVER confirmed/total". */
    String checked() {
        var r = report();
        if (r == null) {
            return "";
        }
        int total = r.checks().size();
        return (r.status() == VerificationStatus.VERIFIED ? "VER " : "UNVER ") + (total - r.rejected().size()) + "/" + total;
    }

    String checkedStyle() {
        var r = report();
        return r == null ? null : r.status() == VerificationStatus.VERIFIED ? "ok" : "bad";
    }

    String proposedBy() {
        return Display.actor(proposal.proposedBy());
    }

    String waiting() {
        return Display.age(Duration.between(proposal.proposedAt(), now));
    }

    /** What approving does, in words; the agent can only propose closing or escalating. */
    String ifApproved() {
        if (proposal.command() instanceof EscalateCaseCommand e) {
            return e.caseId() + " becomes Escalated";
        }
        if (proposal.command() instanceof CloseCaseCommand c) {
            return c.caseId() + " becomes Closed and its alerts close";
        }
        return proposal.command().describe();
    }
}
