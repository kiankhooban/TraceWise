package tracewise.llm;

/** Answers from recordings instead of a model: deterministic tests and demos without an API key. */
public class ReplayLLMClient implements LLMClient {

    private final ReplayStore store;

    public ReplayLLMClient(ReplayStore store) {
        this.store = store;
    }

    @Override
    public LLMResponse chat(LLMRequest request) {
        return store.find(request).orElseThrow(() -> new LLMException(
                "No recorded response for this request. A recording only answers the same request: the same "
                        + "data, and no case decided since it was recorded (similar_cases would differ). Run once with "
                        + "recording on to create it", false, null));
    }

    @Override
    public String modelId() {
        return "replay";
    }
}
