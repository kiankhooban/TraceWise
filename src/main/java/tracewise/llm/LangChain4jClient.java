package tracewise.llm;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.ArrayList;
import java.util.List;

/**
 * Adapter: makes a LangChain4j ChatModel (the adaptee) usable as an LLMClient (the target), so
 * LangChain4j types never leave this class. Subclasses choose the provider.
 */
public abstract class LangChain4jClient implements LLMClient {

    private final ChatModel model;
    private final String modelId;

    protected LangChain4jClient(ChatModel model, String modelId) {
        this.model = model;
        this.modelId = modelId;
    }

    @Override
    public LLMResponse chat(LLMRequest request) {
        return toLLMResponse(model.chat(toChatRequest(request)));
    }

    @Override
    public String modelId() {
        return modelId;
    }

    private static ChatRequest toChatRequest(LLMRequest request) {
        var messages = new ArrayList<ChatMessage>();
        if (request.systemPrompt() != null) {
            messages.add(SystemMessage.from(request.systemPrompt()));
        }
        for (LLMMessage m : request.messages()) {
            messages.add(switch (m) {
                case LLMMessage.User u -> UserMessage.from(u.text());
                case LLMMessage.Assistant a -> a.toolCalls().isEmpty() ? AiMessage.from(a.text())
                        : AiMessage.from(a.text(), a.toolCalls().stream().map(LangChain4jClient::toRequest).toList());
                case LLMMessage.ToolResult r -> ToolExecutionResultMessage.from(r.callId(), r.toolName(), r.content());
            });
        }
        var builder = ChatRequest.builder().messages(messages);
        if (!request.tools().isEmpty()) {
            builder.toolSpecifications(request.tools().stream().map(LangChain4jClient::toSpecification).toList());
        }
        return builder.build();
    }

    private static LLMResponse toLLMResponse(ChatResponse response) {
        AiMessage ai = response.aiMessage();
        List<ToolCall> calls = ai.hasToolExecutionRequests() ? ai.toolExecutionRequests().stream()
                .map(r -> new ToolCall(r.id(), r.name(), r.arguments())).toList() : List.of();
        var usage = response.tokenUsage() == null ? TokenUsage.NONE : new TokenUsage(
                orZero(response.tokenUsage().inputTokenCount()), orZero(response.tokenUsage().outputTokenCount()));
        return new LLMResponse(ai.text(), calls, usage);
    }

    private static ToolExecutionRequest toRequest(ToolCall call) {
        return ToolExecutionRequest.builder().id(call.id()).name(call.name()).arguments(call.argumentsJson()).build();
    }

    private static ToolSpecification toSpecification(ToolSchema schema) {
        var params = JsonObjectSchema.builder();
        for (ToolParameter p : schema.parameters()) {
            switch (p.type()) {
                case STRING -> params.addStringProperty(p.name(), p.description());
                case INTEGER -> params.addIntegerProperty(p.name(), p.description());
                case NUMBER -> params.addNumberProperty(p.name(), p.description());
                case BOOLEAN -> params.addBooleanProperty(p.name(), p.description());
            }
        }
        params.required(schema.parameters().stream().filter(ToolParameter::required).map(ToolParameter::name).toList());
        return ToolSpecification.builder().name(schema.name()).description(schema.description())
                .parameters(params.build()).build();
    }

    private static int orZero(Integer n) {
        return n == null ? 0 : n;
    }
}
