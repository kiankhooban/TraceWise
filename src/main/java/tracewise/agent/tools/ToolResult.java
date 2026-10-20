package tracewise.agent.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/** What the agent gets back from a tool call: data, or (error true) a message it can act on. */
public record ToolResult(String content, boolean error) {

    public static ToolResult ok(String content) {
        return new ToolResult(content, false);
    }

    private static final ObjectMapper JSON = new ObjectMapper();

    /** Data for the model as JSON: maps, lists, strings, numbers. */
    public static ToolResult json(Object data) {
        try {
            return ok(JSON.writeValueAsString(data));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    public static ToolResult error(String message) {
        return new ToolResult(message, true);
    }
}
