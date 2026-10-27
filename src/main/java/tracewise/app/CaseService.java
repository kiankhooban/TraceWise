package tracewise.app;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import tracewise.actions.ActionCommand;
import tracewise.actions.AddNoteCommand;
import tracewise.actions.ApprovalQueue;
import tracewise.actions.CloseCaseCommand;
import tracewise.actions.CommandDispatcher;
import tracewise.actions.DispatchResult;
import tracewise.actions.ProposalStatus;
import tracewise.actions.ReopenCaseCommand;
import tracewise.audit.AuditEntry;
import tracewise.audit.AuditFilter;
import tracewise.audit.AuditLog;
import tracewise.cases.state.CaseStatus;
import tracewise.domain.Actor;
import tracewise.domain.Case;
import tracewise.persistence.CaseRepository;

/**
 * F08: case operations for people. Each is a command submitted through the dispatcher as the session's user,
 * so the change and its audit entry are saved in one transaction.
 */
public class CaseService {

    private final CaseRepository cases;
    private final CommandDispatcher dispatcher;
    private final ApprovalQueue approvals;
    private final AuditLog audit;
    private final Actor user;

    public CaseService(CaseRepository cases, CommandDispatcher dispatcher, ApprovalQueue approvals, AuditLog audit,
            Actor user) {
        this.cases = cases;
        this.dispatcher = dispatcher;
        this.approvals = approvals;
        this.audit = audit;
        this.user = user;
    }

    /** The case's timeline: every audit entry about the case or one of its alerts, in the order it happened. */
    public List<AuditEntry> history(String caseId) {
        var ids = new ArrayList<String>(List.of(caseId));
        ids.addAll(getCase(caseId).alertIds());
        return ids.stream().flatMap(id -> audit.query(new AuditFilter(null, id)).stream())
                .sorted(Comparator.comparing(AuditEntry::timestamp)).toList();
    }

    /** Every case, by id. */
    public List<Case> listCases() {
        return cases.findAll();
    }

    public Case getCase(String caseId) {
        return cases.findById(caseId).orElseThrow(() -> new IllegalArgumentException("No case " + caseId));
    }

    public void addNote(String caseId, String text) {
        require(text, "A note needs some text");
        submit(new AddNoteCommand(caseId, text, user.name()), "note");
    }

    /** An analyst closes a case as a false alarm. A case waiting for approval is closed by deciding its proposal. */
    public void close(String caseId, String reason) {
        require(reason, "A reason is required to close a case");
        if (getCase(caseId).status() == CaseStatus.PENDING_APPROVAL) {
            String waiting = approvals.forCase(caseId).stream().filter(p -> p.status() == ProposalStatus.PENDING)
                    .map(p -> p.id()).findFirst().orElse("a proposal");
            throw new IllegalStateException("Case " + caseId + " is waiting for approval of " + waiting
                    + ": approve or reject that proposal instead");
        }
        submit(new CloseCaseCommand(caseId), reason);
    }

    public void reopen(String caseId, String reason) {
        require(reason, "A reason is required to reopen a case");
        submit(new ReopenCaseCommand(caseId, reason), reason);
    }

    private void submit(ActionCommand command, String justification) {
        DispatchResult r = dispatcher.submit(command, user, justification);
        if (r.status() == DispatchResult.Status.REJECTED) {
            throw new IllegalStateException(r.message());
        }
    }

    private static void require(String text, String message) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
