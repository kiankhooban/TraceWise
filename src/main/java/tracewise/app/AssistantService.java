package tracewise.app;

import java.math.BigDecimal;
import java.util.function.Supplier;
import tracewise.agent.AgentOutcome;
import tracewise.agent.AgentRunner;
import tracewise.agent.AgentTrace;
import tracewise.agent.AssistantAnswer;
import tracewise.agent.AssistantContext;
import tracewise.agent.AssistantTask;
import tracewise.agent.ConversationMemory;
import tracewise.agent.OutcomeStatus;
import tracewise.audit.AuditLog;
import tracewise.llm.ModelConfig;
import tracewise.verification.ClaimVerifier;

/**
 * F10: answers the analyst's questions with the shared agent loop and read-only tools, verifies the cited
 * claims (F05) and keeps the session's conversation so follow-up questions work.
 */
public class AssistantService {

    static final int HISTORY = 5;

    private final Supplier<AgentRunner> runner;
    private final ClaimVerifier verifier;
    private final AuditLog audit;
    private final ModelConfig pricing;
    private final BigDecimal cadPerUsd;
    private final ConversationMemory memory = new ConversationMemory();

    public AssistantService(Supplier<AgentRunner> runner, ClaimVerifier verifier, AuditLog audit, ModelConfig pricing,
            BigDecimal cadPerUsd) {
        this.runner = runner;
        this.verifier = verifier;
        this.audit = audit;
        this.pricing = pricing;
        this.cadPerUsd = cadPerUsd;
    }

    /** Throws IllegalStateException when the run fails or its answer cannot be read; nothing is remembered then. */
    public AssistantAnswer ask(String question, AssistantContext context) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("Ask a question");
        }
        var task = new AssistantTask(question.strip(), context, memory.recent(HISTORY));
        var trace = new AgentTrace("assistant", pricing, cadPerUsd);
        trace.addListener(audit);
        AgentOutcome outcome;
        try {
            outcome = runner.get().run(task, trace);
        } catch (RuntimeException e) {
            throw new IllegalStateException("The assistant failed: " + e.getMessage(), e);
        }
        if (outcome.status() != OutcomeStatus.COMPLETED && outcome.status() != OutcomeStatus.INCOMPLETE) {
            throw new IllegalStateException("The assistant could not answer (" + outcome.status() + "): "
                    + outcome.finalText());
        }
        AssistantAnswer answer;
        try {
            answer = task.parseAnswer(outcome.finalText(), trace);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("The assistant's answer is not in the expected format: " + e.getMessage());
        }
        answer.attachVerification(verifier.verify(answer.claims(), answer.totals()));
        memory.append(question.strip(), answer);
        return answer;
    }
}
