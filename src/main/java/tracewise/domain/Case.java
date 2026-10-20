package tracewise.domain;

import java.time.Instant;
import java.util.List;
import tracewise.cases.state.CaseState;
import tracewise.cases.state.CaseStatus;

/** The investigation behind one or more alerts. Its lifecycle is delegated to its CaseState. */
public class Case {

    private final String id;
    private final Instant openedAt;
    private final List<String> alertIds;
    private CaseState state;

    public Case(String id, Instant openedAt, List<String> alertIds) {
        this(id, openedAt, alertIds, CaseStatus.OPEN);
    }

    /** For a case loaded from storage. */
    public Case(String id, Instant openedAt, List<String> alertIds, CaseStatus status) {
        this.id = id;
        this.openedAt = openedAt;
        this.alertIds = List.copyOf(alertIds);
        this.state = CaseState.of(status);
    }

    public void startInvestigation() {
        state.startInvestigation(this);
    }

    public void submitForApproval() {
        state.submitForApproval(this);
    }

    public void returnToInvestigation() {
        state.returnToInvestigation(this);
    }

    public void escalate() {
        state.escalate(this);
    }

    public void close() {
        state.close(this);
    }

    public void reopen(String reason) {
        state.reopen(this, reason);
    }

    public CaseStatus status() {
        return state.status();
    }

    /** Called only by CaseState subclasses, which live in another package. */
    public void changeState(CaseState next) {
        state = next;
    }

    public String id() {
        return id;
    }

    public Instant openedAt() {
        return openedAt;
    }

    public List<String> alertIds() {
        return alertIds;
    }
}
