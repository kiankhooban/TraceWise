package tracewise.llm;

import dev.langchain4j.model.anthropic.AnthropicChatModel;
import java.time.Duration;

/** Claude through LangChain4j. Retries are left to RetryingLLMClient, so LangChain4j's own are off. */
public class AnthropicLLMClient extends LangChain4jClient {

    /** 4096 cut off 5 of 31 final answers on busy IBM accounts; the prompt also bounds the citations. */
    static final int MAX_OUTPUT_TOKENS = 8192;

    public AnthropicLLMClient(ModelConfig config, String apiKey) {
        super(AnthropicChatModel.builder().apiKey(apiKey).modelName(config.modelName()).maxTokens(MAX_OUTPUT_TOKENS)
                .timeout(Duration.ofSeconds(90)).maxRetries(0).build(), config.modelName());
    }
}
