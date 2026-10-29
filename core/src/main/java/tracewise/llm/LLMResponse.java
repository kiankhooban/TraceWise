package tracewise.llm;

import java.util.List;

/** text is empty (never null) when the model only calls tools. */
public record LLMResponse(String text, List<ToolCall> toolCalls, TokenUsage usage) {

    public LLMResponse {
        text = text == null ? "" : text;
        toolCalls = List.copyOf(toolCalls);
    }

    public boolean hasToolCalls() {
        return !toolCalls.isEmpty();
    }
}
