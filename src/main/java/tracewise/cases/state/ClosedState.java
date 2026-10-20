package tracewise.cases.state;

import tracewise.domain.Case;

public class ClosedState extends CaseState {

    @Override
    public void reopen(Case c, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A reason is required to reopen a case");
        }
        c.changeState(new OpenState());
    }

    @Override
    public CaseStatus status() {
        return CaseStatus.CLOSED;
    }
}
