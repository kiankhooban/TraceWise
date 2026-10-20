package tracewise.cases.state;

import tracewise.domain.Case;

public class UnderInvestigationState extends CaseState {

    /** Investigating again (for example after a failed run) keeps the case where it is. */
    @Override
    public void startInvestigation(Case c) {
    }

    @Override
    public void submitForApproval(Case c) {
        c.changeState(new PendingApprovalState());
    }

    @Override
    public void close(Case c) {
        c.changeState(new ClosedState());
    }

    @Override
    public CaseStatus status() {
        return CaseStatus.UNDER_INVESTIGATION;
    }
}
