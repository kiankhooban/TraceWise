package tracewise.agent;

import java.util.ArrayList;
import java.util.List;
import tracewise.agent.tools.ToolRegistry;
import tracewise.agent.tools.ToolResult;
import tracewise.llm.LLMClient;
import tracewise.llm.LLMException;
import tracewise.llm.LLMMessage;
import tracewise.llm.LLMRequest;
import tracewise.llm.LLMResponse;
import tracewise.llm.ToolCall;
import tracewise.llm.ToolSchema;

/**
 * TraceWise's own tool-calling loop (not LangChain4j AiServices), so every call passes through the
 * registry's validation, the trace, and the turn and cost limits.
 */
public class AgentRunner {

    static final String WRAP_UP = "You have reached the turn or cost limit for this run. Do not call any more tools. "
            + "Give your best answer now from the evidence gathered so far, in the required format.";

    private final LLMClient llm;
    private final ToolRegistry tools;
    private final AgentLimits limits;
    private volatile boolean cancelled;

    public AgentRunner(LLMClient llm, ToolRegistry tools, AgentLimits limits) {
        this.llm = llm;
        this.tools = tools;
        this.limits = limits;
    }

    /** Stops the current run after the step in progress. */
    public void cancel() {
        cancelled = true;
    }

    public AgentOutcome run(AgentTask task, AgentTrace trace) {
        cancelled = false;
        List<ToolSchema> schemas = tools.schemas(task.toolNames());
        var messages = new ArrayList<LLMMessage>(List.of(new LLMMessage.User(task.initialMessage())));
        boolean corrected = false;
        for (int turn = 1; ; turn++) {
            if (cancelled) {
                return finish(trace, OutcomeStatus.CANCELLED, "Cancelled by the analyst");
            }
            boolean wrapUp = turn > limits.maxTurns() || trace.costCad().compareTo(limits.maxCostCad()) >= 0;
            if (wrapUp) {
                messages.add(new LLMMessage.User(WRAP_UP));
            }
            LLMResponse response;
            try {
                // tools stay declared on the wrap-up call: the API rejects tool results without tool definitions
                response = llm.chat(new LLMRequest(task.systemPrompt(), messages, schemas));
            } catch (LLMException e) {
                return finish(trace, OutcomeStatus.FAILED, "The model call failed: " + e.getMessage());
            }
            trace.addUsage(response.usage());
            if (wrapUp) {
                return finish(trace, OutcomeStatus.INCOMPLETE, response.text());
            }
            if (!response.hasToolCalls()) {
                String problem = task.outputProblem(response.text());
                if (problem == null) {
                    return finish(trace, OutcomeStatus.COMPLETED, response.text());
                }
                if (corrected) {
                    return finish(trace, OutcomeStatus.FAILED, "The answer was not in the required format: " + problem);
                }
                corrected = true;
                messages.add(new LLMMessage.Assistant(response.text(), List.of()));
                messages.add(new LLMMessage.User("Your answer is not in the required format: " + problem
                        + ". Reply again with the complete answer in the required format."));
                continue;
            }
            messages.add(new LLMMessage.Assistant(response.text(), response.toolCalls()));
            for (ToolCall call : response.toolCalls()) {
                if (cancelled) {
                    return finish(trace, OutcomeStatus.CANCELLED, "Cancelled by the analyst");
                }
                long start = System.nanoTime();
                ToolResult result = tools.execute(call);
                trace.record(new TraceStep(trace.subject(), turn, call.name(), call.argumentsJson(), result.content(),
                        result.error(), (System.nanoTime() - start) / 1_000_000));
                messages.add(new LLMMessage.ToolResult(call.id(), call.name(), result.content()));
            }
        }
    }

    private static AgentOutcome finish(AgentTrace trace, OutcomeStatus status, String text) {
        var outcome = new AgentOutcome(trace.subject(), status, text);
        trace.finish(outcome);
        return outcome;
    }
}
