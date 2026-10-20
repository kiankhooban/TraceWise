package tracewise.llm;

import java.time.Duration;

/** Retries retryable failures up to maxRetries times, waiting baseDelay, then twice that, between tries. */
public class RetryingLLMClient extends LLMClientDecorator {

    private final int maxRetries = 2;
    private final Duration baseDelay;

    public RetryingLLMClient(LLMClient inner) {
        this(inner, Duration.ofSeconds(2));
    }

    public RetryingLLMClient(LLMClient inner, Duration baseDelay) {
        super(inner);
        this.baseDelay = baseDelay;
    }

    @Override
    public LLMResponse chat(LLMRequest request) {
        for (int attempt = 0; ; attempt++) {
            try {
                return inner.chat(request);
            } catch (LLMException e) {
                if (!e.retryable() || attempt == maxRetries) {
                    throw e;
                }
                pause(baseDelay.multipliedBy(1L << attempt));
            }
        }
    }

    private static void pause(Duration delay) {
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // a cancelled investigation must stop, not keep retrying
            throw new LLMException("Interrupted while waiting to retry", false, e);
        }
    }
}
