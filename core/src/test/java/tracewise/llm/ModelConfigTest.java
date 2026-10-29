package tracewise.llm;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ModelConfigTest {

    /** Stage 1: Opus is an optional upgrade selected through configuration, compared with Sonnet in F12. */
    @Test
    void opusCanBeSelectedWithItsPrices() {
        var opus = ModelConfig.of("anthropic", "claude-opus-5-5", false);
        assertEquals(0, new BigDecimal("4").compareTo(opus.inputPricePerMTok()));
        assertEquals(0, new BigDecimal("20").compareTo(opus.outputPricePerMTok()));
    }

    @Test
    void anUnknownClaudeModelIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> ModelConfig.of("anthropic", "claude-unknown", false));
    }

    /** A local or in-house OpenAI-compatible model has no published price: its runs cost nothing here. */
    @Test
    void anOpenAiCompatibleModelNeedsNoPublishedPrice() {
        var local = ModelConfig.of("openai", "llama-3.1-8b", false);
        assertEquals(0, BigDecimal.ZERO.compareTo(local.inputPricePerMTok()));
        assertEquals(0, BigDecimal.ZERO.compareTo(local.outputPricePerMTok()));
    }

    /** Found in review 11: a recorded OpenAI-compatible run could not be replayed under its own model name. */
    @Test
    void aRecordedOpenAiCompatibleRunReplaysUnderItsModel() {
        assertEquals(0, BigDecimal.ZERO.compareTo(ModelConfig.of("replay", "llama-3.1-8b", false).inputPricePerMTok()));
        assertEquals(0, BigDecimal.ZERO.compareTo(ModelConfig.of("OpenAI", "llama-3.1-8b", false).inputPricePerMTok()));
        assertThrows(IllegalArgumentException.class, () -> ModelConfig.of("replay", "claude-unknown", false));
    }
}
