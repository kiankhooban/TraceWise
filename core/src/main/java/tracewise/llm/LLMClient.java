package tracewise.llm;

/** The only way TraceWise talks to a language model; no class outside this package sees LangChain4j. */
public interface LLMClient {

    LLMResponse chat(LLMRequest request);

    String modelId();
}
