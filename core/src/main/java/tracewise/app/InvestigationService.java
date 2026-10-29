package tracewise.app;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;
import tracewise.actions.ActionCommand;
import tracewise.actions.ApprovalQueue;
import tracewise.actions.CloseCaseCommand;
import tracewise.actions.EscalateCaseCommand;
import tracewise.actions.ProposalStatus;
import tracewise.agent.AgentEventListener;
import tracewise.agent.AgentFinding;
import tracewise.agent.AgentOutcome;
import tracewise.agent.AgentRunner;
import tracewise.agent.AgentTrace;
import tracewise.agent.InvestigationResult;
import tracewise.agent.InvestigationTask;
import tracewise.agent.OutcomeStatus;
import tracewise.agent.Verdict;
import tracewise.agent.tools.ActionScope;
import tracewise.agent.tools.SearchIndicatorsTool;
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
    private final ActionScope scope;
    private final ApprovalQueue approvals;
    private final ClaimVerifier verifier;
    private final AuditLog audit;
    private final Actor user;
    private final ModelConfig pricing;
    private final BigDecimal cadPerUsd;
    private volatile String running;
    private volatile String cancelRequested;

    /** runner is supplied lazily so commands that never call the model work without an API key. */
    public InvestigationService(Database db, AlertRepository alerts, CaseRepository cases, Supplier<AgentRunner> runner,
            ActionScope scope, ApprovalQueue approvals, ClaimVerifier verifier, AuditLog audit, Actor user, ModelConfig pricing, BigDecimal cadPerUsd) {
        this.db = db;
        this.alerts = alerts;
        this.cases = cases;
        this.runner = runner;
        this.scope = scope;
        this.approvals = approvals;
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
        if (alertId.equals(cancelRequested)) {
            cancelRequested = null; // cancelled while it waited to start: nothing is run or changed
            var trace = new AgentTrace(alertId, pricing, cadPerUsd);
            trace.finish(new AgentOutcome(alertId, OutcomeStatus.CANCELLED, "Cancelled before it started"));
            return new InvestigationResult(cases.findByAlert(alertId).map(Case::id).orElse(null), OutcomeStatus.CANCELLED,
                    null, trace, "Cancelled before it started");
        }
        AgentRunner agent = runner.get();
        Case c = openCase(alert);

        var task = new InvestigationTask(alert, c.id(), earlierDecisions(c.id()));
        var trace = new AgentTrace(alertId, pricing, cadPerUsd);
        if (listener != null) {
            trace.addListener(listener);
        }
        trace.addListener(audit);
        running = alertId;
        scope.begin(c.id(), alertId);
        AgentOutcome outcome;
        try {
            outcome = agent.run(task, trace);
        } catch (RuntimeException e) {
            // anything other than a model failure (a full disk, a database error) still ends the run on record
            return finished(c, new InvestigationResult(c.id(), OutcomeStatus.FAILED, null, trace,
                    "The run failed: " + e.getMessage()));
        } finally {
            scope.end();
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
        finding.attachVerification(verifier.verify(finding.claims(), finding.totals(), finding.indicators(),
                SearchIndicatorsTool.returnedIndicators(trace.steps())));
        return finished(c, new InvestigationResult(c.id(), outcome.status(), finding, trace,
                finding.verification().summary()));
    }

    /** The audit details of a finished run: the model, then what happened. */
    static String runDetails(String model, String text) {
        return model + RUN_MODEL_END + text;
    }

    /** The model named by runDetails, or null for an entry written before runs named their model. */
    static String modelOf(String details) {
        int end = details == null ? -1 : details.indexOf(RUN_MODEL_END);
        // older entries start with prose such as "The run failed: ...", never with a lower-case model name
        return end > 0 && details.substring(0, end).matches("[a-z0-9.-]+( \\(replay\\))?") ? details.substring(0, end)
                : null;
    }

    private static final String RUN_MODEL_END = ": ";

    /** Decided proposals on the case, so a new run sees what a person already rejected and why. */
    private List<String> earlierDecisions(String caseId) {
        return approvals.forCase(caseId).stream().filter(p -> p.status() != ProposalStatus.PENDING)
                .map(p -> p.command().describe() + ": " + p.status()
                        + (p.reviewer() == null ? "" : " by " + p.reviewer().name())
                        + (p.decisionReason() == null ? "" : " (" + p.decisionReason() + ")"))
                .toList();
    }

    /** Stops the investigation of this alert after its current step, or before it starts if it is still waiting. */
    public void cancel(String alertId) {
        if (alertId.equals(running)) {
            runner.get().cancel();
        } else {
            cancelRequested = alertId;
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

    /** A close or escalation proposal the finding's verdict does not support. */
    private static boolean contradicts(ActionCommand command, Verdict verdict) {
        return command instanceof CloseCaseCommand ? !verdict.supportsClose()
                : command instanceof EscalateCaseCommand && !verdict.supportsEscalation();
    }

    /**
     * Stores the finding on the case as it is now: the agent's actions may have changed the case during the
     * run (a proposal moves it to Pending Approval), so the copy loaded at the start is not written back.
     */
    private InvestigationResult finished(Case started, InvestigationResult result) {
        db.inTransaction(() -> {
            if (result.finding() == null) {
                approvals.expireUnfinished(started.id(), "the investigation that proposed it ended " + result.status()
                        + " without a finding");
            } else {
                Verdict v = result.finding().verdict();
                approvals.expireWaiting(started.id(), command -> contradicts(command, v),
                        "it contradicts the investigation's own verdict " + v);
            }
            Case c = cases.findById(started.id()).orElseThrow();
            if (result.finding() != null) {
                c.addFinding(result.finding());
            }
            cases.save(c);
            String details = result.finding() == null ? result.message()
                    : result.finding().verdict() + ", confidence " + result.finding().confidence() + ", " + result.message();
            audit.append(new AuditEntry(Instant.now(), user, "INVESTIGATION_FINISHED", result.trace().subject(),
                    runDetails(result.trace().pricing().runLabel(), details),
                    result.status().name()));
        });
        return result;
    }
}
