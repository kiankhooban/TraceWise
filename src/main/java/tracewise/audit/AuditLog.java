package tracewise.audit;

import java.time.Instant;
import java.util.List;
import tracewise.agent.AgentEventListener;
import tracewise.agent.AgentOutcome;
import tracewise.agent.TraceStep;
import tracewise.domain.Actor;

/** Append-only record of every import, run, agent step, action and decision. Listens to agent runs (Observer). */
public class AuditLog implements AgentEventListener {

    private final AuditRepository repository;

    public AuditLog(AuditRepository repository) {
        this.repository = repository;
    }

    public void append(AuditEntry entry) {
        repository.append(entry);
    }

    public List<AuditEntry> query(AuditFilter filter) {
        return repository.find(filter);
    }

    @Override
    public void onStep(TraceStep step) {
        append(new AuditEntry(Instant.now(), Actor.AGENT, "AGENT_STEP", step.subject(),
                step.toolName() + " " + step.arguments(), step.error() ? "ERROR" : "OK"));
    }

    @Override
    public void onFinished(AgentOutcome outcome) {
        append(new AuditEntry(Instant.now(), Actor.AGENT, "AGENT_FINISHED", outcome.subject(), outcome.finalText(),
                outcome.status().name()));
    }
}
