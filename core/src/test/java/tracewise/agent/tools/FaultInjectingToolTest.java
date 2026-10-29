package tracewise.agent.tools;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import tracewise.agent.AgentLimits;
import tracewise.agent.AgentRunner;
import tracewise.agent.AgentRunnerTest;
import tracewise.agent.AgentTask;
import tracewise.agent.AgentTrace;
import tracewise.agent.OutcomeStatus;
import tracewise.llm.ToolCall;

/**
 * Stage 1, Decorator: wrapping a tool to make it fail tests what the agent does when a tool fails, without changing
 * any production tool.
 */
class FaultInjectingToolTest {

    final ToolRegistryTest.EchoTool echo = new ToolRegistryTest.EchoTool();

    ToolResult call(FaultInjectingTool.FaultMode mode) {
        var registry = new ToolRegistry();
        registry.register(new FaultInjectingTool(echo, mode));
        return registry.execute(new ToolCall("c1", echo.name(), "{\"account_id\":\"C1\"}"));
    }

    @Test
    void itPresentsItselfAsTheToolItWraps() {
        var faulty = new FaultInjectingTool(echo, FaultInjectingTool.FaultMode.ERROR);
        assertEquals(echo.name(), faulty.name());
        assertEquals(echo.schema(), faulty.schema());
    }

    @Test
    void aCrashReachesTheAgentAsAToolError() {
        var result = call(FaultInjectingTool.FaultMode.CRASH);
        assertTrue(result.error());
        assertTrue(result.content().contains("injected fault"), result.content());
    }

    @Test
    void anErrorResultIsReturnedWithoutRunningTheTool() {
        var result = call(FaultInjectingTool.FaultMode.ERROR);
        assertTrue(result.error());
        assertEquals(0, echo.calls);
    }

    @Test
    void noFaultPassesTheCallThrough() {
        var result = call(FaultInjectingTool.FaultMode.NONE);
        assertFalse(result.error());
        assertEquals(1, echo.calls);
    }

    @Test
    void theAgentRecordsTheFailureAndStillFinishes() {
        var registry = new ToolRegistry();
        registry.register(new FaultInjectingTool(echo, FaultInjectingTool.FaultMode.CRASH));
        var model = new AgentRunnerTest.ScriptedModel().then(AgentRunnerTest.toolCall(echo.name(), "{\"account_id\":\"C1\"}"))
                .then(AgentRunnerTest.answer("VERDICT: INCONCLUSIVE"));
        var trace = new AgentTrace("ALR-00001", AgentRunnerTest.PRICES, BigDecimal.ONE);
        var task = new AgentTask() {
            @Override
            public String systemPrompt() {
                return "You investigate AML alerts.";
            }

            @Override
            public String initialMessage() {
                return "Investigate ALR-00001";
            }

            @Override
            public java.util.Set<String> toolNames() {
                return java.util.Set.of(echo.name());
            }

            @Override
            public String outputProblem(String finalText) {
                return null;
            }
        };

        var outcome = new AgentRunner(model, registry, AgentLimits.DEFAULT).run(task, trace);

        assertEquals(OutcomeStatus.COMPLETED, outcome.status());
        assertTrue(trace.steps().get(0).error());
        assertTrue(trace.steps().get(0).result().contains("injected fault"), trace.steps().get(0).result());
    }
}
