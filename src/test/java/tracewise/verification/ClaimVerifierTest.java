package tracewise.verification;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import tracewise.agent.Claim;
import tracewise.agent.TotalClaim;
import tracewise.monitoring.InMemoryLedger;

class ClaimVerifierTest {

    // t1: CASH -> C1 9500 on 2026-09-01; t2: C1 -> C2 4000 on 2026-09-02
    final ClaimVerifier verifier = new ClaimVerifier(new InMemoryLedger().deposit("C1", "9500", 0).wire("C1", "C2", "4000", 24));

    static Claim claim(String tx, String account, String amount, String date) {
        return new Claim(tx, account, new BigDecimal(amount), LocalDate.parse(date));
    }

    @Test
    void matchingClaimsAreConfirmed() {
        var report = verifier.verify(List.of(claim("t1", "C1", "9500", "2026-09-01"), claim("t2", "C2", "4000.00", "2026-09-02")),
                List.of());
        assertEquals(VerificationStatus.VERIFIED, report.status());
        assertTrue(report.rejected().isEmpty());
        assertEquals("2 of 2 claims confirmed", report.summary());
    }

    @Test
    void roundingWithinOneCentIsAMatch() {
        assertEquals(VerificationStatus.VERIFIED,
                verifier.verify(List.of(claim("t1", "C1", "9500.01", "2026-09-01")), List.of()).status());
        assertEquals(VerificationStatus.PARTIALLY_VERIFIED,
                verifier.verify(List.of(claim("t1", "C1", "9500.02", "2026-09-01")), List.of()).status());
    }

    @Test
    void eachKindOfMismatchIsRejectedWithItsReason() {
        var report = verifier.verify(List.of(
                claim("t9", "C1", "9700", "2026-09-01"),
                claim("t1", "C1", "9000", "2026-09-01"),
                claim("t1", "C1", "9500", "2026-09-05"),
                claim("t1", "C7", "9500", "2026-09-01"),
                claim("t2", "C1", "4000", "2026-09-02")), List.of());

        assertEquals(VerificationStatus.PARTIALLY_VERIFIED, report.status());
        var reasons = report.rejected().stream().map(ClaimCheck::reason).toList();
        assertEquals(4, reasons.size());
        assertTrue(reasons.get(0).contains("no such transaction"), reasons.get(0));
        assertTrue(reasons.get(1).contains("9500"), reasons.get(1));
        assertTrue(reasons.get(2).contains("2026-09-01"), reasons.get(2));
        assertTrue(reasons.get(3).contains("C7"), reasons.get(3));
        assertEquals("1 of 5 claims confirmed", report.summary());
    }

    @Test
    void totalsMustEqualTheSumOfTheirTransactions() {
        var good = new TotalClaim("Money in and out", new BigDecimal("13500"), List.of("t1", "t2"));
        var bad = new TotalClaim("Inflated", new BigDecimal("15000"), List.of("t1", "t2"));
        var missing = new TotalClaim("Ghost", new BigDecimal("9500"), List.of("t1", "t9"));
        var report = verifier.verify(List.of(), List.of(good, bad, missing));

        assertEquals(List.of(true, false, false), report.checks().stream().map(ClaimCheck::confirmed).toList());
        assertTrue(report.checks().get(1).reason().contains("13500"), report.checks().get(1).reason());
        assertTrue(report.checks().get(2).reason().contains("t9"), report.checks().get(2).reason());
    }

    @Test
    void aFindingThatCitesNothingIsUnsupported() {
        var report = verifier.verify(List.of(), List.of());
        assertEquals(VerificationStatus.UNSUPPORTED, report.status());
    }

    @Test
    void totalsCitingNothingOrTheSameTransactionTwiceAreRejected() {
        var empty = new TotalClaim("Nothing", BigDecimal.ZERO, List.of());
        var doubled = new TotalClaim("Doubled", new BigDecimal("19000"), List.of("t1", "t1"));
        var report = verifier.verify(List.of(), List.of(empty, doubled));

        assertEquals(List.of(false, false), report.checks().stream().map(ClaimCheck::confirmed).toList());
        assertTrue(report.checks().get(0).reason().contains("cites no transactions"), report.checks().get(0).reason());
        assertTrue(report.checks().get(1).reason().contains("t1"), report.checks().get(1).reason());
    }
}
