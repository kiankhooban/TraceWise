package tracewise.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import tracewise.llm.ModelConfig;
import tracewise.llm.TokenUsage;

/**
 * Observer subject: the record of one agent run (steps, token usage, outcome). Every recorded step and
 * the finish are pushed to the listeners as they happen.
 */
public class AgentTrace {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000);

    private final String subject;
    private final ModelConfig pricing;
    private final BigDecimal cadPerUsd;
    private final List<TraceStep> steps = new ArrayList<>();
    private final List<AgentEventListener> listeners = new ArrayList<>();
    private TokenUsage usage = TokenUsage.NONE;
    private AgentOutcome outcome;
    private int turns;
    private java.time.Instant finishedAt;

    /** pricing gives USD per million tokens; cadPerUsd converts the total to CAD. */
    public AgentTrace(String subject, ModelConfig pricing, BigDecimal cadPerUsd) {
        this.subject = subject;
        this.pricing = pricing;
        this.cadPerUsd = cadPerUsd;
    }

    /** A trace loaded from storage; listeners are not told again. */
    public static AgentTrace restore(String subject, ModelConfig pricing, BigDecimal cadPerUsd, List<TraceStep> steps,
            TokenUsage usage, int turns, AgentOutcome outcome, java.time.Instant finishedAt) {
        var t = new AgentTrace(subject, pricing, cadPerUsd);
        t.steps.addAll(steps);
        t.usage = usage;
        t.turns = turns;
        t.outcome = outcome;
        t.finishedAt = finishedAt;
        return t;
    }

    public void addListener(AgentEventListener listener) {
        listeners.add(listener);
    }

    public void record(TraceStep step) {
        steps.add(step);
        listeners.forEach(l -> l.onStep(step));
    }

    /** The usage of one model response: each call is one turn. */
    public void addUsage(TokenUsage more) {
        turns++;
        usage = new TokenUsage(usage.inputTokens() + more.inputTokens(), usage.outputTokens() + more.outputTokens());
    }

    public void finish(AgentOutcome outcome) {
        this.outcome = outcome;
        this.finishedAt = java.time.Instant.now();
        listeners.forEach(l -> l.onFinished(outcome));
    }

    /** Accounts the agent asked tools about (the account_id argument), in first-seen order. */
    public Set<String> examinedAccounts() {
        var accounts = new LinkedHashSet<String>();
        for (TraceStep step : steps) {
            try {
                var id = JSON.readTree(step.arguments()).get("account_id");
                if (id != null && id.isTextual()) {
                    accounts.add(id.asText());
                }
            } catch (JsonProcessingException e) {
                // malformed arguments were rejected by the registry and name no account
            }
        }
        return accounts;
    }

    public BigDecimal costCad() {
        BigDecimal usd = pricing.inputPricePerMTok().multiply(BigDecimal.valueOf(usage.inputTokens()))
                .add(pricing.outputPricePerMTok().multiply(BigDecimal.valueOf(usage.outputTokens())))
                .divide(MILLION, MathContext.DECIMAL64);
        return usd.multiply(cadPerUsd);
    }

    public ModelConfig pricing() {
        return pricing;
    }

    public BigDecimal cadPerUsd() {
        return cadPerUsd;
    }

    public String subject() {
        return subject;
    }

    public List<TraceStep> steps() {
        return List.copyOf(steps);
    }

    public TokenUsage usage() {
        return usage;
    }

    /** Model calls made in the run. */
    public int turns() {
        return turns;
    }

    /** Null while the run is still going, or for a run stored before finish times were recorded. */
    public java.time.Instant finishedAt() {
        return finishedAt;
    }

    /** Null while the run is still going. */
    public AgentOutcome outcome() {
        return outcome;
    }
}
