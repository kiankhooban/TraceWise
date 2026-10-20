package tracewise.app;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;
import tracewise.agent.AgentEventListener;
import tracewise.agent.AgentFinding;
import tracewise.agent.AgentOutcome;
import tracewise.agent.AgentRunner;
import tracewise.agent.AgentTrace;
import tracewise.agent.InvestigationResult;
import tracewise.agent.InvestigationTask;
import tracewise.agent.OutcomeStatus;
import tracewise.audit.AuditEntry;
import tracewise.audit.AuditLog;
import tracewise.domain.Actor;
import tracewise.domain.Alert;
import tracewise.domain.AlertStatus;
import tracewise.domain.Case;
import tracewise.llm.ModelConfig;
import tracewise.persistence.AlertRepository;
import tracewise.persistence.CaseRepository;
import tracewise.persistence.Database;
import tracewise.verification.ClaimVerifier;

/**
 * F03: runs the agent on one alert, verifies its finding (F05) and stores it on the alert's case.
 * One investigation runs at a time per service.
 */
public class InvestigationService {

    private final Database db;
    private final AlertRepository alerts;
    private final CaseRepository cases;
    private final Supplier<AgentRunner> runner;
    private final ClaimVerifier verifier;
    private final AuditLog audit;
    private final Actor user;
    private final ModelConfig pricing;
    private final BigDecimal cadPerUsd;
    private volatile String running;

    /** runner is supplied lazily so commands that never call the model work without an API key. */
    public InvestigationService(Database db, AlertRepository alerts, CaseRepository cases, Supplier<AgentRunner> runner,
            ClaimVerifier verifier, AuditLog audit, Actor user, ModelConfig pricing, BigDecimal cadPerUsd) {
        this.db = db;
        this.alerts = alerts;
        this.cases = cases;
        this.runner = runner;
        this.verifier = verifier;
        this.audit = audit;
        this.user = user;
        this.pricing = pricing;
        this.cadPerUsd = cadPerUsd;
    }

    /** listener may be null. */
    public InvestigationResult investigate(String alertId, AgentEventListener listener) {
        Alert alert = alerts.findById(alertId).orElseThrow(() -> new IllegalArgumentException("No alert " + alertId));
        if (alert.status() == AlertStatus.CLOSED) {
            throw new IllegalStateException("Alert " + alertId + " is closed");
        }
        AgentRunner agent = runner.get();
        Case c = openCase(alert);

        var task = new InvestigationTask(alert);
        var trace = new AgentTrace(alertId, pricing, cadPerUsd);
        if (listener != null) {
            trace.addListener(listener);
        }
        trace.addListener(audit);
        running = alertId;
        AgentOutcome outcome;
        try {
            outcome = agent.run(task, trace);
        } finally {
            running = null;
        }

        if (outcome.status() != OutcomeStatus.COMPLETED && outcome.status() != OutcomeStatus.INCOMPLETE) {
            return finished(c, new InvestigationResult(c.id(), outcome.status(), null, trace, outcome.finalText()));
        }
        AgentFinding finding;
        try {
            finding = task.parseFinding(outcome.finalText(), trace);
        } catch (IllegalArgumentException e) {
            return finished(c, new InvestigationResult(c.id(), OutcomeStatus.FAILED, null, trace,
                    "The agent's answer is not a valid finding: " + e.getMessage()));
        }
        finding.attachVerification(verifier.verify(finding.claims(), finding.totals()));
        c.addFinding(finding);
        return finished(c, new InvestigationResult(c.id(), outcome.status(), finding, trace,
                finding.verification().summary()));
    }

    /** Stops the investigation of this alert after its current step, if it is running. */
    public void cancel(String alertId) {
        if (alertId.equals(running)) {
            runner.get().cancel();
        }
    }

    /** The alert's case (created on first investigation, id CAS- plus the alert number), now Under Investigation. */
    private Case openCase(Alert alert) {
        Case c = cases.findByAlert(alert.id())
                .orElseGet(() -> new Case("CAS-" + alert.id().substring(4), Instant.now(), List.of(alert.id())));
        db.inTransaction(() -> {
            c.startInvestigation();
            cases.save(c);
            alerts.updateStatus(alert.id(), AlertStatus.IN_CASE);
            audit.append(new AuditEntry(Instant.now(), user, "INVESTIGATION_STARTED", alert.id(), c.id(), "OK"));
        });
        return c;
    }

    private InvestigationResult finished(Case c, InvestigationResult result) {
        db.inTransaction(() -> {
            cases.save(c);
            String details = result.finding() == null ? result.message()
                    : result.finding().verdict() + ", confidence " + result.finding().confidence() + ", " + result.message();
            audit.append(new AuditEntry(Instant.now(), user, "INVESTIGATION_FINISHED", result.trace().subject(), details,
                    result.status().name()));
        });
        return result;
    }
}
