package tracewise.cases.state;

import tracewise.domain.Case;

public class OpenState extends CaseState {

    @Override
    public void startInvestigation(Case c) {
        c.changeState(new UnderInvestigationState());
    }

    @Override
    public void close(Case c) {
        c.changeState(new ClosedState());
    }

    @Override
    public CaseStatus status() {
        return CaseStatus.OPEN;
    }
}
