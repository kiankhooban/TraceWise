package tracewise.llm;

import dev.langchain4j.model.anthropic.AnthropicChatModel;
import java.time.Duration;

/** Claude through LangChain4j. Retries are left to RetryingLLMClient, so LangChain4j's own are off. */
public class AnthropicLLMClient extends LangChain4jClient {

    public AnthropicLLMClient(ModelConfig config, String apiKey) {
        super(AnthropicChatModel.builder().apiKey(apiKey).modelName(config.modelName()).maxTokens(4096)
                .timeout(Duration.ofSeconds(90)).maxRetries(0).build(), config.modelName());
    }
}
