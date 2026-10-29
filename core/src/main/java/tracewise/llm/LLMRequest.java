package tracewise.llm;

import java.util.List;

/** systemPrompt may be null. tools lists what the model may call this turn. */
public record LLMRequest(String systemPrompt, List<LLMMessage> messages, List<ToolSchema> tools) {

    public LLMRequest {
        messages = List.copyOf(messages);
        tools = List.copyOf(tools);
    }
}
