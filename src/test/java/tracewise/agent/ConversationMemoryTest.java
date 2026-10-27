package tracewise.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class ConversationMemoryTest {

    static AssistantAnswer answer(String text) {
        return new AssistantAnswer(text, List.of(), List.of(), OutcomeStatus.COMPLETED, null);
    }

    @Test
    void keepsTheLastExchangesOldestFirst() {
        var memory = new ConversationMemory();
        memory.append("one?", answer("1"));
        memory.append("two?", answer("2"));
        memory.append("three?", answer("3"));

        assertEquals(List.of("Q: two?\nA: 2", "Q: three?\nA: 3"), memory.recent(2));
        assertEquals(3, memory.recent(10).size());
        assertEquals(List.of(), new ConversationMemory().recent(5));
    }
}
