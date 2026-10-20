package tracewise.agent;

/** One tool call in an agent run. error is true when validation or the tool itself failed. */
public record TraceStep(String subject, int turn, String toolName, String arguments, String result, boolean error,
        long durationMs) {
}
