package tracewise.llm;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.Test;

class RetryingLLMClientTest {

    static final LLMRequest HELLO = new LLMRequest(null, List.of(new LLMMessage.User("hi")), List.of());
    static final LLMResponse OK = new LLMResponse("ok", List.of(), new TokenUsage(3, 1));

    /** Plays back a script of failures and responses, counting calls. */
    static class ScriptedClient implements LLMClient {
        final Deque<Object> script = new ArrayDeque<>();
        int calls;

        ScriptedClient(Object... steps) {
            script.addAll(List.of(steps));
        }

        @Override
        public LLMResponse chat(LLMRequest request) {
            calls++;
            Object next = script.pop();
            if (next instanceof RuntimeException e) {
                throw e;
            }
            return (LLMResponse) next;
        }

        @Override
        public String modelId() {
            return "scripted";
        }
    }

    static LLMException transientFailure() {
        return new LLMException("rate limited", true, null);
    }

    @Test
    void succeedsAfterTwoTransientFailures() {
        var inner = new ScriptedClient(transientFailure(), transientFailure(), OK);
        assertEquals(OK, new RetryingLLMClient(inner, Duration.ZERO).chat(HELLO));
        assertEquals(3, inner.calls);
    }

    @Test
    void givesUpAfterTwoRetries() {
        var inner = new ScriptedClient(transientFailure(), transientFailure(), transientFailure(), OK);
        assertThrows(LLMException.class, () -> new RetryingLLMClient(inner, Duration.ZERO).chat(HELLO));
        assertEquals(3, inner.calls);
    }

    @Test
    void doesNotRetryPermanentFailures() {
        var inner = new ScriptedClient(new LLMException("bad key", false, null), OK);
        assertThrows(LLMException.class, () -> new RetryingLLMClient(inner, Duration.ZERO).chat(HELLO));
        assertEquals(1, inner.calls);
    }

    @Test
    void reportsTheWrappedModel() {
        assertEquals("scripted", new RetryingLLMClient(new ScriptedClient(), Duration.ZERO).modelId());
    }

    @Test
    void anInterruptWhileWaitingStopsRetryingAndKeepsTheInterruptFlag() {
        var inner = new ScriptedClient(transientFailure(), OK);
        Thread.currentThread().interrupt();
        try {
            var e = assertThrows(LLMException.class, () -> new RetryingLLMClient(inner, Duration.ofMillis(5)).chat(HELLO));
            assertFalse(e.retryable());
            assertTrue(Thread.interrupted());
            assertEquals(1, inner.calls);
        } finally {
            Thread.interrupted();
        }
    }
}
