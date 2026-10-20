package tracewise.cases.state;

import tracewise.domain.Case;

/**
 * State pattern: what a case may do next depends on its state. Every transition is refused here;
 * each concrete state overrides only the ones it allows.
 */
public abstract class CaseState {

    public void startInvestigation(Case c) {
        throw new IllegalTransitionException("start investigating", status());
    }

    public void submitForApproval(Case c) {
        throw new IllegalTransitionException("submit for approval", status());
    }

    public void returnToInvestigation(Case c) {
        throw new IllegalTransitionException("return to investigation", status());
    }

    public void escalate(Case c) {
        throw new IllegalTransitionException("escalate", status());
    }

    public void close(Case c) {
        throw new IllegalTransitionException("close", status());
    }

    public void reopen(Case c, String reason) {
        throw new IllegalTransitionException("reopen", status());
    }

    public abstract CaseStatus status();

    /** The state object for a stored status. */
    public static CaseState of(CaseStatus status) {
        return switch (status) {
            case OPEN -> new OpenState();
            case UNDER_INVESTIGATION -> new UnderInvestigationState();
            case PENDING_APPROVAL -> new PendingApprovalState();
            case ESCALATED -> new EscalatedState();
            case CLOSED -> new ClosedState();
        };
    }
}
