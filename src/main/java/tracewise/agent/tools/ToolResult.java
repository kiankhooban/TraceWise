package tracewise.agent.tools;

/** What the agent gets back from a tool call: data, or (error true) a message it can act on. */
public record ToolResult(String content, boolean error) {

    public static ToolResult ok(String content) {
        return new ToolResult(content, false);
    }

    public static ToolResult error(String message) {
        return new ToolResult(message, true);
    }
}
