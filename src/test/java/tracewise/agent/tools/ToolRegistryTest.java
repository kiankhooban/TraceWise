package tracewise.agent.tools;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tracewise.llm.ToolCall;
import tracewise.llm.ToolParameter;
import tracewise.llm.ToolSchema;

class ToolRegistryTest {

    /** Echoes its arguments; can be told to fail like a broken database. */
    static class EchoTool implements AgentTool {
        int calls;
        RuntimeException failure;

        @Override
        public String name() {
            return "echo";
        }

        @Override
        public ToolSchema schema() {
            return new ToolSchema("echo", "Echo the arguments", List.of(
                    new ToolParameter("account_id", ToolParameter.Type.STRING, "Account id", true),
                    new ToolParameter("days", ToolParameter.Type.INTEGER, "Look-back days", false)));
        }

        @Override
        public ToolResult execute(ToolArguments args) {
            calls++;
            if (failure != null) {
                throw failure;
            }
            return ToolResult.ok(args.string("account_id") + "/" + args.integer("days", 90));
        }
    }

    final EchoTool echo = new EchoTool();
    final ToolRegistry registry = new ToolRegistry();

    {
        registry.register(echo);
    }

    ToolResult call(String argumentsJson) {
        return registry.execute(new ToolCall("c1", "echo", argumentsJson));
    }

    @Test
    void runsAToolWithValidArguments() {
        assertEquals(ToolResult.ok("C1/30"), call("{\"account_id\":\"C1\",\"days\":30}"));
        assertEquals(ToolResult.ok("C1/90"), call("{\"account_id\":\"C1\"}"));
        assertEquals(ToolResult.ok("C1/90"), call("{\"account_id\":\"C1\",\"days\":null}"));
    }

    @Test
    void missingRequiredArgumentIsReturnedToTheAgentWithoutRunningTheTool() {
        var result = call("{\"days\":30}");
        assertTrue(result.error());
        assertTrue(result.content().contains("account_id"), result.content());
        assertEquals(0, echo.calls);
    }

    @Test
    void wrongTypeIsRejected() {
        var result = call("{\"account_id\":\"C1\",\"days\":\"thirty\"}");
        assertTrue(result.error());
        assertTrue(result.content().contains("days"), result.content());
        assertEquals(0, echo.calls);
    }

    @Test
    void unknownArgumentIsRejected() {
        var result = call("{\"account_id\":\"C1\",\"limit\":5}");
        assertTrue(result.error());
        assertTrue(result.content().contains("limit"), result.content());
    }

    @Test
    void malformedJsonIsRejected() {
        assertTrue(call("{account_id: C1").error());
        assertTrue(call("[1,2]").error());
        assertEquals(0, echo.calls);
    }

    @Test
    void unknownToolIsReportedToTheAgent() {
        var result = registry.execute(new ToolCall("c1", "delete_everything", "{}"));
        assertTrue(result.error());
        assertTrue(result.content().contains("delete_everything"), result.content());
    }

    @Test
    void toolFailureIsReturnedNotThrown() {
        echo.failure = new IllegalStateException("database is locked");
        var result = call("{\"account_id\":\"C1\"}");
        assertTrue(result.error());
        assertTrue(result.content().contains("database is locked"), result.content());
    }

    @Test
    void schemasListsOnlyTheRequestedTools() {
        assertEquals(List.of(echo.schema()), registry.schemas(Set.of("echo")));
        assertThrows(IllegalArgumentException.class, () -> registry.schemas(Set.of("missing")));
    }
}
