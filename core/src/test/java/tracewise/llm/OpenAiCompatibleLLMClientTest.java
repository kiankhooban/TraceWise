package tracewise.llm;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Stage 1, Adapter: any provider with an OpenAI-compatible API (a local model server, a bank's own gateway) can run
 * the agent. Tested against a fake local server that answers in that API's format; no model is called.
 */
class OpenAiCompatibleLLMClientTest {

    static final String TOOL_CALL = """
            {"id":"r1","object":"chat.completion","created":1,"model":"local-model","choices":[{"index":0,
            "message":{"role":"assistant","content":null,"tool_calls":[{"id":"call_1","type":"function",
            "function":{"name":"account_history","arguments":"{\\"account_id\\":\\"C1\\"}"}}]},
            "finish_reason":"tool_calls"}],"usage":{"prompt_tokens":12,"completion_tokens":5,"total_tokens":17}}""";

    HttpServer server;
    final AtomicReference<String> request = new AtomicReference<>();
    final AtomicReference<String> authorization = new AtomicReference<>();

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            request.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = TOOL_CALL.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
    }

    static final ModelConfig CONFIG = new ModelConfig("openai", "local-model", BigDecimal.ZERO, BigDecimal.ZERO, false);

    @Test
    void theAgentsRequestAndToolCallsGoThroughTheOpenAiFormat() {
        var client = new OpenAiCompatibleLLMClient(CONFIG, baseUrl(), "test-key");
        var schema = new ToolSchema("account_history", "Transactions of an account",
                List.of(new ToolParameter("account_id", ToolParameter.Type.STRING, "Account id", true)));

        LLMResponse response = client.chat(new LLMRequest("You investigate AML alerts.",
                List.of(new LLMMessage.User("Investigate ALR-00001")), List.of(schema)));

        assertEquals(List.of(new ToolCall("call_1", "account_history", "{\"account_id\":\"C1\"}")), response.toolCalls());
        assertEquals(new TokenUsage(12, 5), response.usage());
        assertEquals("local-model", client.modelId());
        assertEquals("Bearer test-key", authorization.get());
        assertTrue(request.get().contains("\"account_history\""), request.get());
        assertTrue(request.get().contains("You investigate AML alerts."), request.get());
    }
}
