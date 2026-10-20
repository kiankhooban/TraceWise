package tracewise.llm;

import static org.junit.jupiter.api.Assertions.*;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.exception.AuthenticationException;
import dev.langchain4j.exception.RateLimitException;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.List;
import org.junit.jupiter.api.Test;

class LangChain4jClientTest {

    /** Records the request and answers with a canned response. */
    static class FakeChatModel implements ChatModel {
        ChatRequest received;
        ChatResponse answer;
        RuntimeException failure;

        @Override
        public ChatResponse doChat(ChatRequest request) {
            received = request;
            if (failure != null) {
                throw failure;
            }
            return answer;
        }
    }

    final FakeChatModel model = new FakeChatModel();
    final LangChain4jClient client = new LangChain4jClient(model, "test-model") {
    };

    static final ToolSchema BALANCE = new ToolSchema("get_balance", "Balance of one account",
            List.of(new ToolParameter("account_id", ToolParameter.Type.STRING, "The account id", true),
                    new ToolParameter("days", ToolParameter.Type.INTEGER, "Look-back in days", false)));

    @Test
    void translatesTheWholeConversationAndTheTools() {
        model.answer = ChatResponse.builder().aiMessage(AiMessage.from("done")).build();
        client.chat(new LLMRequest("You are an AML investigator.", List.of(
                new LLMMessage.User("Check C1"),
                new LLMMessage.Assistant("Looking it up", List.of(new ToolCall("call-1", "get_balance", "{\"account_id\":\"C1\"}"))),
                new LLMMessage.ToolResult("call-1", "get_balance", "{\"balance\":1234}")), List.of(BALANCE)));

        var messages = model.received.messages();
        assertEquals("You are an AML investigator.", ((SystemMessage) messages.get(0)).text());
        assertEquals("Check C1", ((UserMessage) messages.get(1)).singleText());
        var ai = (AiMessage) messages.get(2);
        assertEquals("Looking it up", ai.text());
        assertEquals("call-1", ai.toolExecutionRequests().get(0).id());
        assertEquals("{\"account_id\":\"C1\"}", ai.toolExecutionRequests().get(0).arguments());
        var result = (ToolExecutionResultMessage) messages.get(3);
        assertEquals("call-1", result.id());
        assertEquals("get_balance", result.toolName());
        assertEquals("{\"balance\":1234}", result.text());

        var spec = model.received.toolSpecifications().get(0);
        assertEquals("get_balance", spec.name());
        assertEquals("Balance of one account", spec.description());
        JsonObjectSchema params = spec.parameters();
        assertEquals(List.of("account_id"), params.required());
        assertEquals(2, params.properties().size());
    }

    @Test
    void translatesToolCallsAndUsageBack() {
        model.answer = ChatResponse.builder()
                .aiMessage(AiMessage.from(List.of(ToolExecutionRequest.builder().id("t9").name("get_balance")
                        .arguments("{\"account_id\":\"C7\"}").build())))
                .tokenUsage(new dev.langchain4j.model.output.TokenUsage(120, 30)).build();
        LLMResponse r = client.chat(new LLMRequest("sys", List.of(new LLMMessage.User("hi")), List.of()));
        assertEquals("", r.text());
        assertEquals(List.of(new ToolCall("t9", "get_balance", "{\"account_id\":\"C7\"}")), r.toolCalls());
        assertEquals(new TokenUsage(120, 30), r.usage());
        assertEquals("test-model", client.modelId());
    }

    @Test
    void aSystemPromptIsOptional() {
        model.answer = ChatResponse.builder().aiMessage(AiMessage.from("ok")).build();
        client.chat(new LLMRequest(null, List.of(new LLMMessage.User("hi")), List.of()));
        assertInstanceOf(UserMessage.class, model.received.messages().get(0));
    }

    static final LLMRequest HELLO = new LLMRequest(null, List.of(new LLMMessage.User("hi")), List.of());

    @Test
    void transientProviderFailuresBecomeRetryable() {
        model.failure = new RateLimitException("slow down");
        var e = assertThrows(LLMException.class, () -> client.chat(HELLO));
        assertTrue(e.retryable());
    }

    @Test
    void permanentProviderFailuresAreNotRetryable() {
        model.failure = new AuthenticationException("bad key");
        var e = assertThrows(LLMException.class, () -> client.chat(HELLO));
        assertFalse(e.retryable());
    }
}
