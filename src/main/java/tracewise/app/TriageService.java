package tracewise.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import tracewise.agent.AgentEventListener;
import tracewise.agent.InvestigationResult;
import tracewise.agent.OutcomeStatus;
import tracewise.agent.TraceStep;
import tracewise.agent.TriageReport;
import tracewise.agent.TriageReport.EndReason;
import tracewise.agent.TriageReport.Item;
import tracewise.agent.tools.AddToWatchlistTool;
import tracewise.agent.tools.CreateTaskTool;
import tracewise.agent.tools.LinkAlertsTool;
import tracewise.agent.tools.ProposeCloseTool;
import tracewise.agent.tools.ProposeEscalationTool;
import tracewise.agent.tools.SetPriorityTool;
import tracewise.audit.AuditEntry;
import tracewise.audit.AuditLog;
import tracewise.cases.state.CaseStatus;
import tracewise.domain.Actor;
import tracewise.domain.Alert;
import tracewise.domain.AlertStatus;
import tracewise.domain.Case;
import tracewise.persistence.AlertRepository;
import tracewise.persistence.CaseRepository;
import tracewise.persistence.ExactJson;

/**
 * F07: investigates the open alerts one after another in risk order, within an alert-count and a cost
 * limit. A failed alert is recorded and left open; stop() ends triage after the current alert.
 */
public class TriageService {

    private final AlertRepository alerts;
    private final CaseRepository cases;
    private final InvestigationService investigations;
    private final AuditLog audit;
    private final Actor user;
    private volatile boolean stopRequested;

    public TriageService(AlertRepository alerts, CaseRepository cases, InvestigationService investigations, AuditLog audit,
            Actor user) {
        this.alerts = alerts;
        this.cases = cases;
        this.investigations = investigations;
        this.audit = audit;
        this.user = user;
    }

    /** listener (may be null) sees every agent step and the end of each alert's run. */
    public TriageReport triage(TriageLimits limits, AgentEventListener listener) {
        try {
            return run(limits, listener);
        } finally {
            stopRequested = false; // cleared when triage ends: a stop sent while it was still queued is not lost
        }
    }

    private TriageReport run(TriageLimits limits, AgentEventListener listener) {
        long start = System.nanoTime();
        List<Alert> open = alerts.find(limits.filter()).stream().filter(this::needsInvestigation).toList();
        var items = new ArrayList<Item>();
        var actions = new ArrayList<TraceStep>();
        int escalations = 0;
        int closes = 0;
        var triagedCases = new HashSet<String>();
        BigDecimal cost = BigDecimal.ZERO;
        int failuresInARow = 0;
        EndReason end = open.isEmpty() ? EndReason.NOTHING_TO_TRIAGE : EndReason.COMPLETED;
        for (Alert queued : open) {
            if (stopRequested) {
                end = EndReason.STOPPED;
                break;
            }
            // the queue was read at the start; earlier runs may have linked this alert into a case since
            Alert alert = alerts.findById(queued.id()).orElse(queued);
            Optional<String> caseId = cases.findByAlert(alert.id()).map(Case::id);
            if (!needsInvestigation(alert) || caseId.filter(triagedCases::contains).isPresent()) {
                continue;
            }
            if (items.size() >= limits.maxAlerts()) {
                end = EndReason.ALERT_LIMIT;
                break;
            }
            if (cost.compareTo(limits.maxCostCad()) >= 0) {
                end = EndReason.COST_LIMIT;
                break;
            }
            Item item;
            try {
                InvestigationResult r = investigations.investigate(alert.id(), listener);
                cost = cost.add(r.trace().costCad());
                triagedCases.add(r.caseId());
                actions.addAll(r.trace().steps()); // autonomous actions took effect whatever the outcome
                // proposals from a run without a finding, or contradicting its verdict, were expired
                if (r.finding() != null && r.finding().verdict().supportsEscalation()) {
                    escalations += count(r.trace().steps(), ProposeEscalationTool.NAME);
                }
                if (r.finding() != null && r.finding().verdict().supportsClose()) {
                    closes += count(r.trace().steps(), ProposeCloseTool.NAME);
                }
                item = new Item(alert.id(), r.caseId(), r.status(), r.finding() == null ? null : r.finding().verdict(),
                        r.finding() == null ? null : r.finding().verification().summary(), r.message(),
                        r.trace().costCad());
            } catch (RuntimeException e) {
                item = new Item(alert.id(), caseId.orElse(null), OutcomeStatus.FAILED, null, null, e.getMessage(),
                        BigDecimal.ZERO);
            }
            items.add(item);
            failuresInARow = item.status() == OutcomeStatus.FAILED ? failuresInARow + 1 : 0;
            if (failuresInARow >= TriageReport.MAX_FAILURES_IN_A_ROW) {
                end = EndReason.REPEATED_FAILURES;
                break;
            }
        }
        var report = new TriageReport(end, items, cost, (System.nanoTime() - start) / 1_000_000,
                escalations, closes, linkedAlerts(actions),
                count(actions, SetPriorityTool.NAME) + count(actions, AddToWatchlistTool.NAME)
                        + count(actions, CreateTaskTool.NAME));
        audit.append(new AuditEntry(Instant.now(), user, "TRIAGE_FINISHED", "alert queue", report.summary(), end.name()));
        return report;
    }

    /** Ends triage after the alert being investigated now. */
    public void stop() {
        stopRequested = true;
    }

    /**
     * New alerts; alerts whose case is Open (never investigated, or reopened by an analyst); and alerts whose case
     * is under investigation with no finding (an earlier run failed).
     */
    private boolean needsInvestigation(Alert alert) {
        if (alert.status() == AlertStatus.NEW) {
            return true;
        }
        return alert.status() == AlertStatus.IN_CASE && cases.findByAlert(alert.id())
                .filter(c -> c.status() == CaseStatus.OPEN
                        || c.status() == CaseStatus.UNDER_INVESTIGATION && c.latestFinding() == null).isPresent();
    }

    private static int count(List<TraceStep> steps, String tool) {
        return (int) steps.stream().filter(s -> !s.error() && s.toolName().equals(tool)).count();
    }

    /** Distinct alerts linked by successful link_alerts calls, read the way the tool reads its argument. */
    private static int linkedAlerts(List<TraceStep> steps) {
        var linked = new HashSet<String>();
        for (TraceStep s : steps) {
            if (!s.error() && s.toolName().equals(LinkAlertsTool.NAME)) {
                try {
                    String ids = ExactJson.mapper().readTree(s.arguments()).path("alert_ids").asText();
                    Arrays.stream(ids.split(",")).map(String::strip).filter(id -> !id.isEmpty()).forEach(linked::add);
                } catch (JsonProcessingException e) {
                    // a successful step had valid arguments; nothing to count otherwise
                }
            }
        }
        return linked.size();
    }
}
