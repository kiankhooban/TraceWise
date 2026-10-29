package tracewise.agent;

import java.util.ArrayList;
import java.util.List;
import tracewise.verification.ClaimCheck;

/** The assistant's conversation in this session, so follow-up questions work. Held in memory only. */
public class ConversationMemory {

    private final List<String> exchanges = new ArrayList<>();

    /** Claims that verification rejected are kept with the answer, so a follow-up is not built on them unknowingly. */
    public synchronized void append(String question, AssistantAnswer answer) {
        var rejected = answer.verification() == null ? List.<ClaimCheck>of() : answer.verification().rejected();
        exchanges.add("Q: " + question + "\nA: " + answer.text() + (rejected.isEmpty() ? ""
                : "\n(Verification rejected: " + String.join("; ", rejected.stream()
                        .map(c -> c.claim() + ": " + c.reason()).toList()) + ")"));
    }

    /** The last n exchanges, oldest first. */
    public synchronized List<String> recent(int n) {
        return List.copyOf(exchanges.subList(Math.max(0, exchanges.size() - n), exchanges.size()));
    }
}
