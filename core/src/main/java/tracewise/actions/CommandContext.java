package tracewise.actions;

import tracewise.cases.state.CaseStatus;
import tracewise.domain.Case;
import tracewise.persistence.AlertRepository;
import tracewise.persistence.CaseRepository;
import tracewise.persistence.TransactionRepository;
import tracewise.retrieval.CaseMemory;

/** What commands may read and change; memory learns from cases as they are closed or escalated. */
public record CommandContext(CaseRepository cases, AlertRepository alerts, TransactionRepository accounts,
        CaseMemory memory) {

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
