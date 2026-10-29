package tracewise.llm;

public record TokenUsage(int inputTokens, int outputTokens) {
    public static final TokenUsage NONE = new TokenUsage(0, 0);
}
