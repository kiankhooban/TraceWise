package tracewise.llm;

import java.time.Duration;

/** Retries retryable failures up to maxRetries times, waiting baseDelay, then twice that, between tries. */
public class RetryingLLMClient extends LLMClientDecorator {

    /** How to wait between tries; tests record the waits instead of sleeping. */
    interface Pause {
        void pause(Duration delay) throws InterruptedException;
    }

    private final int maxRetries = 2;
    private final Duration baseDelay;
    private final Pause pause;

    public RetryingLLMClient(LLMClient inner) {
        this(inner, Duration.ofSeconds(2));
    }

    public RetryingLLMClient(LLMClient inner, Duration baseDelay) {
        this(inner, baseDelay, Thread::sleep);
    }

    RetryingLLMClient(LLMClient inner, Duration baseDelay, Pause pause) {
        super(inner);
        this.baseDelay = baseDelay;
        this.pause = pause;
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
                waitBeforeRetry(baseDelay.multipliedBy(1L << attempt));
            }
        }
    }

    private void waitBeforeRetry(Duration delay) {
        try {
            pause.pause(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // a cancelled investigation must stop, not keep retrying
            throw new LLMException("Interrupted while waiting to retry", false, e);
        }
    }
}
