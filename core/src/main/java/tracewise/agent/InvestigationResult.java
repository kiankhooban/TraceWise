package tracewise.agent;

/**
 * What an investigation produced. finding is null when the run failed or was cancelled; message then
 * says why. trace holds every step either way.
 */
public record InvestigationResult(String caseId, OutcomeStatus status, AgentFinding finding, AgentTrace trace,
        String message) {
}
