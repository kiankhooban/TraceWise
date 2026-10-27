package tracewise.agent;

import java.util.ArrayList;
import java.util.List;

/** The assistant's conversation in this session, so follow-up questions work. Held in memory only. */
public class ConversationMemory {

    private final List<String> exchanges = new ArrayList<>();

    public synchronized void append(String question, AssistantAnswer answer) {
        exchanges.add("Q: " + question + "\nA: " + answer.text());
    }

    /** The last n exchanges, oldest first. */
    public synchronized List<String> recent(int n) {
        return List.copyOf(exchanges.subList(Math.max(0, exchanges.size() - n), exchanges.size()));
    }
}
