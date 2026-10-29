package tracewise.cases.state;

import tracewise.domain.Case;

public class PendingApprovalState extends CaseState {

    @Override
    public void escalate(Case c) {
        c.changeState(new EscalatedState());
    }

    @Override
    public void close(Case c) {
        c.changeState(new ClosedState());
    }

    /** A rejected proposal sends the case back. */
    @Override
    public void returnToInvestigation(Case c) {
        c.changeState(new UnderInvestigationState());
    }

    @Override
    public CaseStatus status() {
        return CaseStatus.PENDING_APPROVAL;
    }
}
