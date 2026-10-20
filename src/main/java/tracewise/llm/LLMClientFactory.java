package tracewise.llm;

/** Builds the client a configuration asks for, stacking decorators: recording outside retrying. */
public class LLMClientFactory {

    private final String apiKey;
    private final ReplayStore replayStore;

    /** apiKey may be null when only replay is used. */
    public LLMClientFactory(String apiKey, ReplayStore replayStore) {
        this.apiKey = apiKey;
        this.replayStore = replayStore;
    }

    public LLMClient create(ModelConfig config) {
        LLMClient client = switch (config.provider().toLowerCase()) {
            case "anthropic" -> new RetryingLLMClient(new AnthropicLLMClient(config, requireKey()));
            case "replay" -> new ReplayLLMClient(replayStore);
            default -> throw new IllegalArgumentException("Unknown LLM provider: " + config.provider());
        };
        return config.record() ? new RecordingLLMClient(client, replayStore) : client;
    }

    private String requireKey() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("ANTHROPIC_API_KEY is not set; copy .env.example to .env and add your key");
        }
        return apiKey;
    }
}
