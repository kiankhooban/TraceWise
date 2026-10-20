package tracewise.actions;

import tracewise.cases.state.CaseStatus;
import tracewise.domain.Case;
import tracewise.persistence.AlertRepository;
import tracewise.persistence.CaseRepository;
import tracewise.persistence.TransactionRepository;

/** What commands may read and change. */
public record CommandContext(CaseRepository cases, AlertRepository alerts, TransactionRepository accounts) {

    Case caseFor(String caseId) {
        return cases.findById(caseId).orElseThrow(() -> new IllegalArgumentException("No case " + caseId));
    }

    /** A closed or escalated case takes no further actions. */
    Case activeCase(String caseId) {
        Case c = caseFor(caseId);
        if (c.status() == CaseStatus.CLOSED || c.status() == CaseStatus.ESCALATED) {
            throw new IllegalStateException("Case " + caseId + " is " + c.status());
        }
        return c;
    }
}
