package tracewise.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import tracewise.agent.AgentEventListener;
import tracewise.agent.InvestigationResult;
import tracewise.agent.OutcomeStatus;
import tracewise.agent.TraceStep;
import tracewise.agent.TriageReport;
import tracewise.agent.TriageReport.EndReason;
import tracewise.agent.TriageReport.Item;
import tracewise.audit.AuditEntry;
import tracewise.audit.AuditLog;
import tracewise.cases.state.CaseStatus;
import tracewise.domain.Actor;
import tracewise.domain.Alert;
import tracewise.domain.AlertStatus;
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
        stopRequested = false;
        long start = System.nanoTime();
        List<Alert> open = alerts.find(limits.filter()).stream().filter(this::needsInvestigation).toList();
        var items = new ArrayList<Item>();
        var steps = new ArrayList<TraceStep>();
        BigDecimal cost = BigDecimal.ZERO;
        EndReason end = open.isEmpty() ? EndReason.NOTHING_TO_TRIAGE : EndReason.COMPLETED;
        for (Alert alert : open) {
            if (stopRequested) {
                end = EndReason.STOPPED;
                break;
            }
            if (items.size() >= limits.maxAlerts()) {
                end = EndReason.ALERT_LIMIT;
                break;
            }
            if (cost.compareTo(limits.maxCostCad()) >= 0) {
                end = EndReason.COST_LIMIT;
                break;
            }
            try {
                InvestigationResult r = investigations.investigate(alert.id(), listener);
                cost = cost.add(r.trace().costCad());
                if (r.finding() != null) {
                    steps.addAll(r.trace().steps()); // a run without a finding had its proposals expired
                }
                items.add(new Item(alert.id(), r.caseId(), r.status(),
                        r.finding() == null ? null : r.finding().verdict(),
                        r.finding() == null ? null : r.finding().verification().summary(), r.message(),
                        r.trace().costCad()));
            } catch (RuntimeException e) {
                items.add(new Item(alert.id(), null, OutcomeStatus.FAILED, null, null, e.getMessage(), BigDecimal.ZERO));
            }
        }
        var report = new TriageReport(end, items, cost, (System.nanoTime() - start) / 1_000_000,
                count(steps, "propose_escalation"), count(steps, "propose_close"), linkedAlerts(steps),
                count(steps, "set_priority") + count(steps, "add_to_watchlist") + count(steps, "create_task"));
        audit.append(new AuditEntry(Instant.now(), user, "TRIAGE_FINISHED", "alert queue", report.summary(), end.name()));
        return report;
    }

    /** Ends triage after the alert being investigated now. */
    public void stop() {
        stopRequested = true;
    }

    /** New alerts, and alerts whose case is still open with no finding (an earlier run failed). */
    private boolean needsInvestigation(Alert alert) {
        if (alert.status() == AlertStatus.NEW) {
            return true;
        }
        return alert.status() == AlertStatus.IN_CASE && cases.findByAlert(alert.id())
                .filter(c -> c.status() == CaseStatus.OPEN || c.status() == CaseStatus.UNDER_INVESTIGATION)
                .filter(c -> c.latestFinding() == null).isPresent();
    }

    private static int count(List<TraceStep> steps, String tool) {
        return (int) steps.stream().filter(s -> !s.error() && s.toolName().equals(tool)).count();
    }

    private static int linkedAlerts(List<TraceStep> steps) {
        int linked = 0;
        for (TraceStep s : steps) {
            if (!s.error() && s.toolName().equals("link_alerts")) {
                try {
                    linked += ExactJson.mapper().readTree(s.arguments()).path("alert_ids").asText().split(",").length;
                } catch (JsonProcessingException e) {
                    // a successful step had valid arguments; nothing to count otherwise
                }
            }
        }
        return linked;
    }
}
