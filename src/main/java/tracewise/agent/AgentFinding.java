package tracewise.agent;

import java.util.List;
import tracewise.verification.VerificationReport;

/**
 * The agent's structured conclusion about an alert. incomplete is true when a turn or cost limit forced
 * it early. verification is null until the ClaimVerifier has checked the claims.
 */
public class AgentFinding {

    private final Verdict verdict;
    private final double confidence;
    private final List<String> indicators;
    private final List<Claim> claims;
    private final List<TotalClaim> totals;
    private final String narrative;
    private final boolean incomplete;
    private VerificationReport verification;

    public AgentFinding(Verdict verdict, double confidence, List<String> indicators, List<Claim> claims,
            List<TotalClaim> totals, String narrative, boolean incomplete) {
        this.verdict = verdict;
        this.confidence = confidence;
        this.indicators = List.copyOf(indicators);
        this.claims = List.copyOf(claims);
        this.totals = List.copyOf(totals);
        this.narrative = narrative;
        this.incomplete = incomplete;
    }

    public void attachVerification(VerificationReport report) {
        this.verification = report;
    }

    public VerificationReport verification() {
        return verification;
    }

    public Verdict verdict() {
        return verdict;
    }

    public double confidence() {
        return confidence;
    }

    public List<String> indicators() {
        return indicators;
    }

    public List<Claim> claims() {
        return claims;
    }

    public List<TotalClaim> totals() {
        return totals;
    }

    public String narrative() {
        return narrative;
    }

    public boolean incomplete() {
        return incomplete;
    }
}
