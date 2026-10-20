package tracewise.agent;

import java.util.Set;

/** What an agent run is for: its instructions, opening message, tools, and the shape its answer must have. */
public interface AgentTask {

    String systemPrompt();

    String initialMessage();

    Set<String> toolNames();

    /** Why the final answer is not in the required structure, or null if it is. */
    default String outputProblem(String finalText) {
        return null;
    }
}
