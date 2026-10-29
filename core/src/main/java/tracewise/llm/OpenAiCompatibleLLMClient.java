package tracewise.llm;

import dev.langchain4j.model.openai.OpenAiChatModel;
import java.time.Duration;

/**
 * Any provider with an OpenAI-compatible API (a local model server, a bank's own gateway) through LangChain4j.
 * Retries are left to RetryingLLMClient, so LangChain4j's own are off.
 */
public class OpenAiCompatibleLLMClient extends LangChain4jClient {

    public OpenAiCompatibleLLMClient(ModelConfig config, String baseUrl, String apiKey) {
        super(OpenAiChatModel.builder().baseUrl(baseUrl).apiKey(apiKey).modelName(config.modelName())
                .maxTokens(AnthropicLLMClient.MAX_OUTPUT_TOKENS).timeout(Duration.ofSeconds(90)).maxRetries(0).build(),
                config.modelName());
    }
}
