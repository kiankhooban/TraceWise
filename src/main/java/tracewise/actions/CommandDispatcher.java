package tracewise.actions;

import java.time.Instant;
import tracewise.audit.AuditEntry;
import tracewise.audit.AuditLog;
import tracewise.domain.Actor;
import tracewise.persistence.Database;

/**
 * The Command invoker for every action: validates it, asks the policy for its tier, then runs it or
 * queues it for approval. Each outcome is audited in the same database transaction as the change.
 */
public class CommandDispatcher {

    private final Database db;
    private final CommandContext ctx;
    private final PermissionPolicy policy;
    private final ApprovalQueue queue;
    private final AuditLog audit;

    public CommandDispatcher(Database db, CommandContext ctx, PermissionPolicy policy, ApprovalQueue queue, AuditLog audit) {
        this.db = db;
        this.ctx = ctx;
        this.policy = policy;
        this.queue = queue;
        this.audit = audit;
    }

    public DispatchResult submit(ActionCommand command, Actor actor, String justification) {
        try {
            command.validate(ctx);
            PermissionTier tier = policy.tierFor(command, actor);
            if (tier == PermissionTier.AUTONOMOUS) {
                db.inTransaction(() -> {
                    command.execute(ctx);
                    log(actor, "ACTION_EXECUTED", command, tier + ": " + justification, "OK");
                });
                return new DispatchResult(DispatchResult.Status.EXECUTED, "Done: " + command.describe(), null);
            }
            PendingAction pending = db.inTransaction(() -> {
                PendingAction p = queue.enqueue(command, actor, justification);
                log(actor, "ACTION_PROPOSED", command, tier + " as " + p.id() + ": " + justification, "PENDING");
                return p;
            });
            return new DispatchResult(DispatchResult.Status.PROPOSED, "Proposed for human approval as " + pending.id()
                    + ": " + command.describe(), pending.id());
        } catch (IllegalArgumentException | IllegalStateException e) {
            log(actor, "ACTION_REJECTED", command, e.getMessage(), "REJECTED");
            return new DispatchResult(DispatchResult.Status.REJECTED, "Not done: " + e.getMessage(), null);
        }
    }

    private void log(Actor actor, String event, ActionCommand command, String detail, String outcome) {
        audit.append(new AuditEntry(Instant.now(), actor, event, command.target(), command.describe() + " (" + detail + ")",
                outcome));
    }
}
