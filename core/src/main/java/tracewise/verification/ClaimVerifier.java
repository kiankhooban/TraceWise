package tracewise.verification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import tracewise.agent.Claim;
import tracewise.agent.TotalClaim;
import tracewise.domain.Transaction;
import tracewise.persistence.Ledger;

/** Checks every cited fact against the ledger. Deterministic: it never calls the model. */
public class ClaimVerifier {

    private static final BigDecimal TOLERANCE_CAD = new BigDecimal("0.01");

    private final Ledger ledger;

    public ClaimVerifier(Ledger ledger) {
        this.ledger = ledger;
    }

    public VerificationReport verify(List<Claim> claims, List<TotalClaim> totals) {
        return verify(claims, totals, List.of(), Set.of());
    }

    /** Also checks that each indicator is one the indicator search returned during the run, word for word. */
    public VerificationReport verify(List<Claim> claims, List<TotalClaim> totals, List<String> indicators,
            Set<String> retrievedIndicators) {
        var checks = new ArrayList<ClaimCheck>();
        claims.forEach(c -> checks.add(check(c)));
        totals.forEach(t -> checks.add(check(t)));
        for (String indicator : indicators) {
            checks.add(retrievedIndicators.contains(indicator.strip())
                    ? new ClaimCheck("Indicator: " + indicator, true, "")
                    : rejected("Indicator: " + indicator, "search_indicators did not return it in this investigation"));
        }
        VerificationStatus status = checks.isEmpty() ? VerificationStatus.UNSUPPORTED
                : checks.stream().allMatch(ClaimCheck::confirmed) ? VerificationStatus.VERIFIED
                : VerificationStatus.PARTIALLY_VERIFIED;
        return new VerificationReport(status, checks);
    }

    private ClaimCheck check(Claim claim) {
        Optional<Transaction> found = ledger.findTransaction(claim.transactionId());
        if (found.isEmpty()) {
            return rejected(claim.describe(), "no such transaction in the ledger");
        }
        Transaction t = found.get();
        if (!matches(claim.amountCad(), t.amountCad())) {
            return rejected(claim.describe(), "the ledger amount is " + t.amountCad().toPlainString() + " CAD");
        }
        LocalDate actualDate = LocalDate.ofInstant(t.timestamp(), ZoneOffset.UTC);
        if (!actualDate.equals(claim.date())) {
            return rejected(claim.describe(), "the ledger date is " + actualDate);
        }
        if (!claim.accountId().equals(t.senderAccountId()) && !claim.accountId().equals(t.receiverAccountId())) {
            return rejected(claim.describe(), "account " + claim.accountId() + " is not in this transaction ("
                    + t.senderAccountId() + " to " + t.receiverAccountId() + ")");
        }
        return new ClaimCheck(claim.describe(), true, "");
    }

    private ClaimCheck check(TotalClaim total) {
        if (total.transactionIds().isEmpty()) {
            return rejected(total.describe(), "the total cites no transactions");
        }
        var seen = new HashSet<String>();
        for (String id : total.transactionIds()) {
            if (!seen.add(id)) {
                return rejected(total.describe(), id + " is cited more than once");
            }
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (String id : total.transactionIds()) {
            Optional<Transaction> t = ledger.findTransaction(id);
            if (t.isEmpty()) {
                return rejected(total.describe(), id + " is not in the ledger");
            }
            sum = sum.add(t.get().amountCad());
        }
        if (!matches(total.amountCad(), sum)) {
            return rejected(total.describe(), "the cited transactions sum to " + sum.toPlainString() + " CAD");
        }
        return new ClaimCheck(total.describe(), true, "");
    }

    private static boolean matches(BigDecimal claimed, BigDecimal actual) {
        return claimed.subtract(actual).abs().compareTo(TOLERANCE_CAD) <= 0;
    }

    private static ClaimCheck rejected(String claim, String reason) {
        return new ClaimCheck(claim, false, reason);
    }
}
