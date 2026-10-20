package tracewise.llm;

/** A failed model call. retryable is true for failures worth trying again (rate limit, timeout, server error). */
public class LLMException extends RuntimeException {

    private final boolean retryable;

    public LLMException(String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
    }

    public boolean retryable() {
        return retryable;
    }
}
