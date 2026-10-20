package tracewise.llm;

/** A tool call requested by the model; argumentsJson is the raw JSON object it produced. */
public record ToolCall(String id, String name, String argumentsJson) {
}
