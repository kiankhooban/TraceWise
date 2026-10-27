package tracewise.agent;

import java.util.List;
import tracewise.verification.VerificationReport;

/**
 * The assistant's reply (F10): its text, the transactions and totals it cites, how the run ended, and the
 * trace. verification is null until the ClaimVerifier has checked the claims.
 */
public class AssistantAnswer {

    private final String text;
    private final List<Claim> claims;
    private final List<TotalClaim> totals;
    private final OutcomeStatus status;
    private final AgentTrace trace;
    private VerificationReport verification;

    public AssistantAnswer(String text, List<Claim> claims, List<TotalClaim> totals, OutcomeStatus status,
            AgentTrace trace) {
        this.text = text;
        this.claims = List.copyOf(claims);
        this.totals = List.copyOf(totals);
        this.status = status;
        this.trace = trace;
    }

    public void attachVerification(VerificationReport report) {
        this.verification = report;
    }

    public String text() {
        return text;
    }

    public List<Claim> claims() {
        return claims;
    }

    public List<TotalClaim> totals() {
        return totals;
    }

    /** COMPLETED, or INCOMPLETE when a turn or cost limit forced the answer early. */
    public OutcomeStatus status() {
        return status;
    }

    public AgentTrace trace() {
        return trace;
    }

    public VerificationReport verification() {
        return verification;
    }
}
