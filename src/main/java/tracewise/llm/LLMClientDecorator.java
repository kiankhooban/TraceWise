package tracewise.llm;

/** Decorator: wraps another LLMClient and passes calls through; subclasses add behaviour around chat(). */
public abstract class LLMClientDecorator implements LLMClient {

    protected final LLMClient inner;

    protected LLMClientDecorator(LLMClient inner) {
        this.inner = inner;
    }

    @Override
    public LLMResponse chat(LLMRequest request) {
        return inner.chat(request);
    }

    @Override
    public String modelId() {
        return inner.modelId();
    }
}
