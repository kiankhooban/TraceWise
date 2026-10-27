package tracewise.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import tracewise.agent.AgentFinding;
import tracewise.cases.state.CaseState;
import tracewise.cases.state.CaseStatus;

/** The investigation behind one or more alerts. Its lifecycle is delegated to its CaseState. */
public class Case {

    private final String id;
    private final Instant openedAt;
    private final List<String> alertIds;
    private final List<AgentFinding> findings;
    private final List<FollowUpTask> tasks;
    private final List<CaseNote> notes;
    private CaseState state;

    public Case(String id, Instant openedAt, List<String> alertIds) {
        this(id, openedAt, alertIds, CaseStatus.OPEN, List.of());
    }

    public Case(String id, Instant openedAt, List<String> alertIds, CaseStatus status) {
        this(id, openedAt, alertIds, status, List.of());
    }

    public Case(String id, Instant openedAt, List<String> alertIds, CaseStatus status, List<AgentFinding> findings) {
        this(id, openedAt, alertIds, status, findings, List.of());
    }

    public Case(String id, Instant openedAt, List<String> alertIds, CaseStatus status, List<AgentFinding> findings,
            List<FollowUpTask> tasks) {
        this(id, openedAt, alertIds, status, findings, tasks, List.of());
    }

    /** For a case loaded from storage. */
    public Case(String id, Instant openedAt, List<String> alertIds, CaseStatus status, List<AgentFinding> findings,
            List<FollowUpTask> tasks, List<CaseNote> notes) {
        this.id = id;
        this.openedAt = openedAt;
        this.alertIds = new ArrayList<>(alertIds);
        this.state = CaseState.of(status);
        this.findings = new ArrayList<>(findings);
        this.tasks = new ArrayList<>(tasks);
        this.notes = new ArrayList<>(notes);
    }

    /** Adds another alert to this case; an alert already linked is ignored. */
    public void linkAlert(String alertId) {
        if (!alertIds.contains(alertId)) {
            alertIds.add(alertId);
        }
    }

    public void addTask(FollowUpTask task) {
        tasks.add(task);
    }

    public void addNote(CaseNote note) {
        notes.add(note);
    }

    /** Oldest first. */
    public List<CaseNote> notes() {
        return List.copyOf(notes);
    }

    public List<FollowUpTask> tasks() {
        return List.copyOf(tasks);
    }

    public void addFinding(AgentFinding finding) {
        findings.add(finding);
    }

    /** Null before the first finding. */
    public AgentFinding latestFinding() {
        return findings.isEmpty() ? null : findings.get(findings.size() - 1);
    }

    /** Oldest first. */
    public List<AgentFinding> findings() {
        return List.copyOf(findings);
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
        return List.copyOf(alertIds);
    }
}
