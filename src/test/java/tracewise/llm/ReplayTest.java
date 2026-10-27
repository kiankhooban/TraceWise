package tracewise.llm;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReplayTest {

    @TempDir
    Path dir;

    static final LLMRequest ASK = new LLMRequest("You are an AML investigator.",
            List.of(new LLMMessage.User("Investigate ALR-00001"),
                    new LLMMessage.Assistant("", List.of(new ToolCall("c1", "account_history", "{\"account_id\":\"S-C1\"}"))),
                    new LLMMessage.ToolResult("c1", "account_history", "[]")),
            List.of(new ToolSchema("account_history", "Transactions of one account",
                    List.of(new ToolParameter("account_id", ToolParameter.Type.STRING, "Account id", true)))));
    static final LLMResponse ANSWER = new LLMResponse("Structuring below $10,000.",
            List.of(new ToolCall("c2", "counterparties", "{\"account_id\":\"S-C1\"}")), new TokenUsage(812, 64));

    /** Answers every request with ANSWER and counts calls. */
    static class FixedClient implements LLMClient {
        int calls;

        @Override
        public LLMResponse chat(LLMRequest request) {
            calls++;
            return ANSWER;
        }

        @Override
        public String modelId() {
            return "fixed";
        }
    }

    @Test
    void storedResponseSurvivesANewStoreOnTheSameDirectory() {
        new ReplayStore(dir).save(ASK, ANSWER);
        assertEquals(ANSWER, new ReplayStore(dir).find(ASK).orElseThrow());
    }

    @Test
    void aDifferentRequestHasNoRecording() {
        new ReplayStore(dir).save(ASK, ANSWER);
        var other = new LLMRequest(ASK.systemPrompt(), List.of(new LLMMessage.User("Investigate ALR-00002")), ASK.tools());
        assertTrue(new ReplayStore(dir).find(other).isEmpty());
    }

    @Test
    void recordedRunReplaysWithoutTheModel() {
        var model = new FixedClient();
        var recording = new RecordingLLMClient(model, new ReplayStore(dir));
        assertEquals(ANSWER, recording.chat(ASK));
        assertEquals("fixed", recording.modelId());

        assertEquals(ANSWER, new ReplayLLMClient(new ReplayStore(dir)).chat(ASK));
        assertEquals(1, model.calls);
    }

    @Test
    void replayFailsClearlyWhenNothingWasRecorded() {
        var e = assertThrows(LLMException.class, () -> new ReplayLLMClient(new ReplayStore(dir)).chat(ASK));
        assertFalse(e.retryable());
        assertTrue(e.getMessage().contains("No recorded response"), e.getMessage());
    }

    static LLMRequest withToolResult(String tool, String content) {
        return new LLMRequest("sys", List.of(new LLMMessage.User("q"),
                new LLMMessage.Assistant("", List.of(new ToolCall("c1", tool, "{}"))),
                new LLMMessage.ToolResult("c1", tool, content)), List.of());
    }

    @Test
    void resultsOfToolsThatDifferAcrossMachinesAreLeftOutOfTheMatch() {
        var store = new ReplayStore(dir, java.util.Set.of("search_indicators"));
        store.save(withToolResult("search_indicators", "{\"score\":0.831}"), ANSWER);

        assertEquals(ANSWER, store.find(withToolResult("search_indicators", "{\"score\":0.830}")).orElseThrow());
    }

    @Test
    void resultsOfOtherToolsMustMatchExactly() {
        var store = new ReplayStore(dir, java.util.Set.of("search_indicators"));
        store.save(withToolResult("account_history", "{\"amount\":9500}"), ANSWER);

        assertTrue(store.find(withToolResult("account_history", "{\"amount\":9501}")).isEmpty());
    }
}
