package tracewise.verification;

import java.util.List;

public record VerificationReport(VerificationStatus status, List<ClaimCheck> checks) {

    public VerificationReport {
        checks = List.copyOf(checks);
    }

    public List<ClaimCheck> rejected() {
        return checks.stream().filter(c -> !c.confirmed()).toList();
    }

    /** For the banner, for example "11 of 12 claims confirmed". */
    public String summary() {
        return (checks.size() - rejected().size()) + " of " + checks.size() + " claims confirmed";
    }
}
