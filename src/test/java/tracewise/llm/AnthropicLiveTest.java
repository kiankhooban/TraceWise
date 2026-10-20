package tracewise.llm;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tracewise.app.EnvFile;

/** Calls the real API (a few hundred tokens of Haiku 5.5). Runs only with TRACEWISE_LIVE_LLM=1. */
@EnabledIfEnvironmentVariable(named = "TRACEWISE_LIVE_LLM", matches = "1")
class AnthropicLiveTest {

    @Test
    void completesAToolRoundTrip() {
        String key = EnvFile.read(Path.of(".env")).get("ANTHROPIC_API_KEY");
        assertNotNull(key, "ANTHROPIC_API_KEY missing from .env");
        var client = new AnthropicLLMClient(new ModelConfig("anthropic", "claude-haiku-5-5",
                new BigDecimal("0.10"), new BigDecimal("0.50")), key);
        var tool = new ToolSchema("get_balance", "Returns the current balance in CAD of one account",
                List.of(new ToolParameter("account_id", ToolParameter.Type.STRING, "The account id", true)));
        var messages = new ArrayList<LLMMessage>(List.of(new LLMMessage.User(
                "What is the balance of account C1? Use the tool, then answer with the number only.")));

        LLMResponse first = client.chat(new LLMRequest("You are a careful banking assistant.", messages, List.of(tool)));
        assertEquals(1, first.toolCalls().size(), first.toString());
        ToolCall call = first.toolCalls().get(0);
        assertEquals("get_balance", call.name());
        assertTrue(call.argumentsJson().contains("C1"), call.argumentsJson());

        messages.add(new LLMMessage.Assistant(first.text(), first.toolCalls()));
        messages.add(new LLMMessage.ToolResult(call.id(), call.name(), "{\"balance_cad\": 4821.37}"));
        LLMResponse second = client.chat(new LLMRequest("You are a careful banking assistant.", messages, List.of(tool)));
        assertTrue(second.text().replace(",", "").contains("4821.37"), second.text());
        assertTrue(second.usage().inputTokens() > 0);
        System.out.printf("live round trip OK; usage %s then %s%n", first.usage(), second.usage());
    }
}
