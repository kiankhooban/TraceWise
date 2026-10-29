package tracewise.llm;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LLMClientFactoryTest {

    @TempDir
    Path dir;

    static ModelConfig config(String provider, boolean record) {
        return new ModelConfig(provider, "claude-haiku-5-5", new BigDecimal("0.10"), new BigDecimal("0.50"), record);
    }

    @Test
    void anthropicCallsAreRetried() {
        var client = new LLMClientFactory("not-a-real-key", new ReplayStore(dir)).create(config("anthropic", false));
        assertInstanceOf(RetryingLLMClient.class, client);
        assertEquals("claude-haiku-5-5", client.modelId());
    }

    @Test
    void recordingWrapsTheRetriesSoOnlyFinalAnswersAreSaved() {
        var client = new LLMClientFactory("not-a-real-key", new ReplayStore(dir)).create(config("anthropic", true));
        assertInstanceOf(RecordingLLMClient.class, client);
        assertInstanceOf(RetryingLLMClient.class, ((LLMClientDecorator) client).inner);
    }

    @Test
    void replayNeedsNoKey() {
        assertInstanceOf(ReplayLLMClient.class, new LLMClientFactory(null, new ReplayStore(dir)).create(config("replay", false)));
    }

    @Test
    void anthropicWithoutAKeyNamesTheSetting() {
        var e = assertThrows(IllegalStateException.class,
                () -> new LLMClientFactory(null, new ReplayStore(dir)).create(config("anthropic", false)));
        assertTrue(e.getMessage().contains("ANTHROPIC_API_KEY"), e.getMessage());
    }

    @Test
    void unknownProviderIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new LLMClientFactory("k", new ReplayStore(dir)).create(config("gemini", false)));
    }
}
