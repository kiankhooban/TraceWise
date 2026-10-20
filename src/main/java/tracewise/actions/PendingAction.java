package tracewise.actions;

import java.time.Instant;
import tracewise.domain.Actor;

/** A proposed command waiting for a human decision. Once decided it never changes again. */
public class PendingAction {

    private final String id;
    private final ActionCommand command;
    private final String justification;
    private final Actor proposedBy;
    private final Instant proposedAt;
    private ProposalStatus status;
    private Actor reviewer;
    private String decisionReason;
    private Instant decidedAt;

    public PendingAction(String id, ActionCommand command, String justification, Actor proposedBy, Instant proposedAt) {
        this(id, command, justification, proposedBy, proposedAt, ProposalStatus.PENDING, null, null, null);
    }

    /** For a proposal loaded from storage. */
    public PendingAction(String id, ActionCommand command, String justification, Actor proposedBy, Instant proposedAt,
            ProposalStatus status, Actor reviewer, String decisionReason, Instant decidedAt) {
        this.id = id;
        this.command = command;
        this.justification = justification;
        this.proposedBy = proposedBy;
        this.proposedAt = proposedAt;
        this.status = status;
        this.reviewer = reviewer;
        this.decisionReason = decisionReason;
        this.decidedAt = decidedAt;
    }

    public void markApproved(Actor reviewer) {
        decide(ProposalStatus.APPROVED, reviewer, null);
    }

    public void markRejected(Actor reviewer, String reason) {
        decide(ProposalStatus.REJECTED, reviewer, reason);
    }

    /** reason says what changed, for the audit trail and the approvals list. */
    public void markExpired(String reason) {
        decide(ProposalStatus.EXPIRED, null, reason);
    }

    private void decide(ProposalStatus next, Actor by, String reason) {
        if (status != ProposalStatus.PENDING) {
            throw new IllegalStateException("Proposal " + id + " is already " + status);
        }
        status = next;
        reviewer = by;
        decisionReason = reason;
        decidedAt = Instant.now();
    }

    public String id() {
        return id;
    }

    public ActionCommand command() {
        return command;
    }

    public String justification() {
        return justification;
    }

    public Actor proposedBy() {
        return proposedBy;
    }

    public Instant proposedAt() {
        return proposedAt;
    }

    public ProposalStatus status() {
        return status;
    }

    /** Null until approved or rejected. */
    public Actor reviewer() {
        return reviewer;
    }

    public String decisionReason() {
        return decisionReason;
    }

    public Instant decidedAt() {
        return decidedAt;
    }
}
