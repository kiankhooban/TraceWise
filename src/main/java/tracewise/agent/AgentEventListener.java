package tracewise.agent;

/** Observer of an agent run: the GUI's step panel, the CLI and the audit log. */
public interface AgentEventListener {

    void onStep(TraceStep step);

    void onFinished(AgentOutcome outcome);
}
