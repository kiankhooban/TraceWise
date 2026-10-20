package tracewise.llm;

import java.util.List;

/** One turn of the conversation sent to the model. */
public sealed interface LLMMessage {

    record User(String text) implements LLMMessage {
    }

    /** What the model said earlier, including any tool calls it made. */
    record Assistant(String text, List<ToolCall> toolCalls) implements LLMMessage {
        public Assistant {
            toolCalls = List.copyOf(toolCalls);
        }
    }

    /** The result of one tool call, matched to the call by its id. */
    record ToolResult(String callId, String toolName, String content) implements LLMMessage {
    }
}
