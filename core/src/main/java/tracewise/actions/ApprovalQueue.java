package tracewise.actions;

import java.time.Instant;
import java.util.List;
import java.util.function.Predicate;
import tracewise.audit.AuditEntry;
import tracewise.audit.AuditLog;
import tracewise.cases.state.CaseStatus;
import tracewise.domain.Actor;
import tracewise.domain.Case;
import tracewise.persistence.ApprovalRepository;
import tracewise.persistence.Database;

/**
 * The deferred Command invoker: holds proposed commands until an analyst or supervisor approves
 * (the command is re-validated and run) or rejects them (a reason is required).
 */
public class ApprovalQueue {

    private final Database db;
    private final ApprovalRepository repository;
    private final CommandContext ctx;
    private final AuditLog audit;

    public ApprovalQueue(Database db, ApprovalRepository repository, CommandContext ctx, AuditLog audit) {
        this.db = db;
        this.repository = repository;
        this.ctx = ctx;
        this.audit = audit;
    }

    /**
     * Stores the proposal and moves its case to Pending Approval; the caller's transaction covers both. An escalated
     * case stays escalated: its decision is made, and only its report's finalisation can wait.
     */
    public PendingAction enqueue(ActionCommand command, Actor actor, String justification) {
        String caseId = command.targetCaseId()
                .orElseThrow(() -> new IllegalArgumentException("Only actions on a case can wait for approval"));
        Case c = ctx.caseFor(caseId);
        if (c.status() != CaseStatus.ESCALATED) {
            c.submitForApproval();
            ctx.cases().save(c);
        }
        var pending = new PendingAction(repository.nextId(), command, justification, actor, Instant.now());
        repository.save(pending);
        return pending;
    }

    public void approve(String pendingId, Actor reviewer) {
        requireHuman(reviewer);
        PendingAction p = pendingOrThrow(pendingId);
        try {
            db.inTransaction(() -> {
                p.command().validate(ctx);
                p.command().execute(ctx);
                p.markApproved(reviewer);
                repository.save(p);
                log(reviewer, "PROPOSAL_APPROVED", p, "approved and done", "OK");
            });
        } catch (IllegalArgumentException | IllegalStateException e) {
            PendingAction stale = pendingOrThrow(pendingId);
            db.inTransaction(() -> {
                stale.markExpired(e.getMessage());
                repository.save(stale);
                returnToInvestigationIfWaiting(stale);
                log(reviewer, "PROPOSAL_EXPIRED", stale, "the case changed since the proposal: " + e.getMessage(), "EXPIRED");
            });
        }
    }

    public void reject(String pendingId, Actor reviewer, String reason) {
        requireHuman(reviewer);
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A reason is required to reject a proposal");
        }
        PendingAction p = pendingOrThrow(pendingId);
        db.inTransaction(() -> {
            p.markRejected(reviewer, reason);
            repository.save(p);
            returnToInvestigationIfWaiting(p);
            log(reviewer, "PROPOSAL_REJECTED", p, "rejected: " + reason, "REJECTED");
        });
    }

    /** A case still waiting on this proposal goes back to investigation, so it is never left stuck. */
    private void returnToInvestigationIfWaiting(PendingAction p) {
        p.command().targetCaseId().map(ctx::caseFor).filter(c -> c.status() == CaseStatus.PENDING_APPROVAL)
                .ifPresent(c -> {
                    c.returnToInvestigation();
                    ctx.cases().save(c);
                });
    }

    /** Every proposal on the case, oldest first. shortcut: filters all proposals; add a case column if they grow. */
    public List<PendingAction> forCase(String caseId) {
        return repository.findAll().stream().filter(p -> p.command().targetCaseId().filter(caseId::equals).isPresent())
                .toList();
    }

    /**
     * Expires the case's waiting proposals when the run that made them ended without a finding, so nobody
     * approves an action whose evidence was never produced. Runs in the caller's transaction.
     */
    public void expireUnfinished(String caseId, String reason) {
        expireWaiting(caseId, command -> true, reason);
    }

    /** Expires the case's waiting proposals whose command matches. */
    public void expireWaiting(String caseId, Predicate<ActionCommand> which, String reason) {
        for (PendingAction p : forCase(caseId)) {
            if (p.status() == ProposalStatus.PENDING && which.test(p.command())) {
                p.markExpired(reason);
                repository.save(p);
                returnToInvestigationIfWaiting(p);
                log(Actor.SYSTEM, "PROPOSAL_EXPIRED", p, reason, "EXPIRED");
            }
        }
    }

    /** Waiting proposals, oldest first. */
    public List<PendingAction> pending() {
        return repository.findPending();
    }

    public PendingAction find(String pendingId) {
        return repository.findById(pendingId).orElseThrow(() -> new IllegalArgumentException("No proposal " + pendingId));
    }

    private PendingAction pendingOrThrow(String pendingId) {
        PendingAction p = find(pendingId);
        if (p.status() != ProposalStatus.PENDING) {
            throw new IllegalStateException("Proposal " + pendingId + " is already " + p.status());
        }
        return p;
    }

    private static void requireHuman(Actor reviewer) {
        if (!reviewer.isHuman()) {
            throw new IllegalArgumentException("Only an analyst or supervisor can decide a proposal");
        }
    }

    private void log(Actor actor, String event, PendingAction p, String detail, String outcome) {
        audit.append(new AuditEntry(Instant.now(), actor, event, p.command().target(),
                p.id() + " " + p.command().describe() + " (" + detail + ")", outcome));
    }
}
