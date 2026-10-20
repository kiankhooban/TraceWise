package tracewise.agent;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tracewise.agent.tools.AgentTool;
import tracewise.agent.tools.ToolArguments;
import tracewise.agent.tools.ToolRegistry;
import tracewise.agent.tools.ToolResult;
import tracewise.llm.LLMClient;
import tracewise.llm.LLMException;
import tracewise.llm.LLMMessage;
import tracewise.llm.LLMRequest;
import tracewise.llm.LLMResponse;
import tracewise.llm.ModelConfig;
import tracewise.llm.TokenUsage;
import tracewise.llm.ToolCall;
import tracewise.llm.ToolParameter;
import tracewise.llm.ToolSchema;

class AgentRunnerTest {

    /** Plays back scripted responses (or failures) and keeps every request it was sent. */
    static class ScriptedModel implements LLMClient {
        final Deque<Object> script = new ArrayDeque<>();
        final List<LLMRequest> requests = new ArrayList<>();

        ScriptedModel then(Object step) {
            script.add(step);
            return this;
        }

        @Override
        public LLMResponse chat(LLMRequest request) {
            requests.add(request);
            Object next = script.isEmpty() ? toolCall("{\"account_id\":\"C1\"}") : script.pop();
            if (next instanceof RuntimeException e) {
                throw e;
            }
            return (LLMResponse) next;
        }

        @Override
        public String modelId() {
            return "scripted";
        }
    }

    static int ids;

    static LLMResponse toolCall(String args) {
        return new LLMResponse("", List.of(new ToolCall("call-" + (++ids), "history", args)), new TokenUsage(1000, 100));
    }

    static LLMResponse answer(String text) {
        return new LLMResponse(text, List.of(), new TokenUsage(1000, 100));
    }

    /** A history tool that can cancel the run while it executes. */
    class HistoryTool implements AgentTool {
        Runnable sideEffect = () -> { };

        @Override
        public String name() {
            return "history";
        }

        @Override
        public ToolSchema schema() {
            return new ToolSchema("history", "Transactions of an account",
                    List.of(new ToolParameter("account_id", ToolParameter.Type.STRING, "Account id", true)));
        }

        @Override
        public ToolResult execute(ToolArguments args) {
            sideEffect.run();
            return ToolResult.ok("3 transactions for " + args.string("account_id"));
        }
    }

    /** Requires the final answer to start with VERDICT. */
    static final AgentTask TASK = new AgentTask() {
        @Override
        public String systemPrompt() {
            return "You investigate AML alerts.";
        }

        @Override
        public String initialMessage() {
            return "Investigate ALR-00001";
        }

        @Override
        public Set<String> toolNames() {
            return Set.of("history");
        }

        @Override
        public String outputProblem(String finalText) {
            return finalText.startsWith("VERDICT") ? null : "start with VERDICT";
        }
    };

    static final ModelConfig PRICES = new ModelConfig("anthropic", "m", new BigDecimal("2"), new BigDecimal("10"), false);

    /** Remembers what listeners are told. */
    static class Recorder implements AgentEventListener {
        final List<TraceStep> steps = new ArrayList<>();
        AgentOutcome finished;

        @Override
        public void onStep(TraceStep step) {
            steps.add(step);
        }

        @Override
        public void onFinished(AgentOutcome outcome) {
            finished = outcome;
        }
    }

    final ScriptedModel model = new ScriptedModel();
    final HistoryTool history = new HistoryTool();
    final Recorder recorder = new Recorder();
    final AgentTrace trace = new AgentTrace("ALR-00001", PRICES, BigDecimal.ONE);

    AgentRunner runner(AgentLimits limits) {
        var registry = new ToolRegistry();
        registry.register(history);
        trace.addListener(recorder);
        return new AgentRunner(model, registry, limits);
    }

    AgentRunner runner() {
        return runner(AgentLimits.DEFAULT);
    }

    @Test
    void callsToolsUntilTheModelAnswers() {
        model.then(toolCall("{\"account_id\":\"C1\"}")).then(answer("VERDICT: ESCALATE"));
        var outcome = runner().run(TASK, trace);

        assertEquals(new AgentOutcome("ALR-00001", OutcomeStatus.COMPLETED, "VERDICT: ESCALATE"), outcome);
        assertEquals(1, trace.steps().size());
        var step = trace.steps().get(0);
        assertEquals("history", step.toolName());
        assertEquals("3 transactions for C1", step.result());
        assertFalse(step.error());
        assertEquals(1, step.turn());
        assertEquals(trace.steps(), recorder.steps);
        assertEquals(outcome, recorder.finished);
        assertEquals(Set.of("C1"), trace.examinedAccounts());
        assertEquals(new TokenUsage(2000, 200), trace.usage());

        var second = model.requests.get(1);
        assertEquals("You investigate AML alerts.", second.systemPrompt());
        assertEquals("history", second.tools().get(0).name());
        var last = second.messages().get(second.messages().size() - 1);
        assertEquals("3 transactions for C1", ((LLMMessage.ToolResult) last).content());
    }

    @Test
    void invalidArgumentsAreRecordedAndTheAgentContinues() {
        model.then(toolCall("{}")).then(answer("VERDICT: DISMISS"));
        var outcome = runner().run(TASK, trace);

        assertEquals(OutcomeStatus.COMPLETED, outcome.status());
        assertTrue(trace.steps().get(0).error());
        assertTrue(trace.steps().get(0).result().contains("account_id"));
    }

    @Test
    void turnLimitAsksForTheBestFindingAndMarksItIncomplete() {
        model.then(toolCall("{\"account_id\":\"C1\"}")).then(toolCall("{\"account_id\":\"C2\"}"))
                .then(answer("VERDICT: INCONCLUSIVE"));
        var outcome = runner(new AgentLimits(2, new BigDecimal("100"))).run(TASK, trace);

        assertEquals(OutcomeStatus.INCOMPLETE, outcome.status());
        assertEquals("VERDICT: INCONCLUSIVE", outcome.finalText());
        assertEquals(3, model.requests.size());
        var wrapUp = (LLMMessage.User) model.requests.get(2).messages().get(model.requests.get(2).messages().size() - 1);
        assertTrue(wrapUp.text().contains("limit"), wrapUp.text());
    }

    @Test
    void costLimitAlsoEndsTheRunIncomplete() {
        // one turn costs 1000 * 2 / 1M + 100 * 10 / 1M = 0.003
        model.then(toolCall("{\"account_id\":\"C1\"}")).then(answer("VERDICT: INCONCLUSIVE"));
        var outcome = runner(new AgentLimits(12, new BigDecimal("0.002"))).run(TASK, trace);

        assertEquals(OutcomeStatus.INCOMPLETE, outcome.status());
        assertEquals(2, model.requests.size());
        assertEquals(new BigDecimal("0.006"), trace.costCad().stripTrailingZeros());
    }

    @Test
    void modelFailureEndsTheRunButKeepsThePartialTrace() {
        model.then(toolCall("{\"account_id\":\"C1\"}")).then(new LLMException("overloaded", true, null));
        var outcome = runner().run(TASK, trace);

        assertEquals(OutcomeStatus.FAILED, outcome.status());
        assertTrue(outcome.finalText().contains("overloaded"));
        assertEquals(1, trace.steps().size());
        assertEquals(outcome, recorder.finished);
    }

    @Test
    void badlyStructuredAnswerIsCorrectedOnce() {
        model.then(answer("I think it is fine")).then(answer("VERDICT: DISMISS"));
        var outcome = runner().run(TASK, trace);

        assertEquals(OutcomeStatus.COMPLETED, outcome.status());
        assertEquals("VERDICT: DISMISS", outcome.finalText());
        var retry = (LLMMessage.User) model.requests.get(1).messages().get(2);
        assertTrue(retry.text().contains("start with VERDICT"), retry.text());
    }

    @Test
    void secondBadlyStructuredAnswerFails() {
        model.then(answer("fine")).then(answer("still fine"));
        assertEquals(OutcomeStatus.FAILED, runner().run(TASK, trace).status());
    }

    @Test
    void cancelStopsAfterTheCurrentStep() {
        var runner = runner();
        history.sideEffect = runner::cancel;
        model.then(toolCall("{\"account_id\":\"C1\"}")).then(toolCall("{\"account_id\":\"C2\"}"));
        var outcome = runner.run(TASK, trace);

        assertEquals(OutcomeStatus.CANCELLED, outcome.status());
        assertEquals(1, trace.steps().size());
        assertEquals(1, model.requests.size());
    }
}
