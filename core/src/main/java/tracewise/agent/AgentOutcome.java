package tracewise.agent;

/** subject is what the agent worked on (for example an alert id); finalText is its answer or why it stopped. */
public record AgentOutcome(String subject, OutcomeStatus status, String finalText) {
}
