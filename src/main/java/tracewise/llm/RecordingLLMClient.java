package tracewise.llm;

/** Decorator: saves every answer of the wrapped client so ReplayLLMClient can play the run back. */
public class RecordingLLMClient extends LLMClientDecorator {

    private final ReplayStore store;

    public RecordingLLMClient(LLMClient inner, ReplayStore store) {
        super(inner);
        this.store = store;
    }

    @Override
    public LLMResponse chat(LLMRequest request) {
        LLMResponse response = inner.chat(request);
        store.save(request, response);
        return response;
    }
}
