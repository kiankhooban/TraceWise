package tracewise.reporting;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import tracewise.agent.AgentFinding;
import tracewise.agent.Claim;
import tracewise.agent.TotalClaim;
import tracewise.agent.Verdict;
import tracewise.domain.PaymentChannel;
import tracewise.domain.Transaction;
import tracewise.verification.ClaimCheck;
import tracewise.verification.VerificationReport;
import tracewise.verification.VerificationStatus;

class StrReportTest {

    static final Transaction T1 = new Transaction("TX-1", Instant.parse("2026-09-01T10:00:00Z"), "CASH", "C1",
            new BigDecimal("9500.00"), new BigDecimal("9500.00"), "CAD", PaymentChannel.CASH, "CA", null);

    static final Claim GOOD = new Claim("TX-1", "C1", new BigDecimal("9500.00"), LocalDate.of(2026, 9, 1));
    static final Claim BAD = new Claim("TX-9", "C1", new BigDecimal("9900.00"), LocalDate.of(2026, 9, 2));
    static final TotalClaim BAD_TOTAL = new TotalClaim("Cash deposits", new BigDecimal("41500.00"), List.of("TX-1"));

    static StrReport report(String grounds, String actions) {
        return StrReport.draft("CAS-00001", "C1", List.of(T1), grounds, actions);
    }

    static AgentFinding finding(List<Claim> claims, List<TotalClaim> totals, List<String> indicators,
            List<ClaimCheck> checks) {
        var f = new AgentFinding(Verdict.ESCALATE, 0.9, indicators, claims, totals, "narrative", false, null);
        f.attachVerification(new VerificationReport(checks.stream().allMatch(ClaimCheck::confirmed)
                ? VerificationStatus.VERIFIED : VerificationStatus.PARTIALLY_VERIFIED, checks));
        return f;
    }

    static AgentFinding verified() {
        return finding(List.of(GOOD), List.of(), List.of(), List.of(new ClaimCheck(GOOD.describe(), true, "")));
    }

    @Test
    void aCompleteReportBackedByAVerifiedFindingHasNoProblems() {
        assertEquals(List.of(), report("Cash deposits just under 10,000.", "Escalated.").finalisationProblems(verified()));
    }

    @Test
    void emptyRequiredFieldsAreListed() {
        var empty = StrReport.draft("CAS-00001", "", List.of(), " ", "");

        assertEquals(List.of("Missing: subject account", "Missing: transactions", "Missing: grounds for suspicion",
                "Missing: actions taken"), empty.finalisationProblems(verified()));
    }

    @Test
    void aRejectedClaimStillInTheTextBlocksFinalisationUntilItIsRemoved() {
        var f = finding(List.of(GOOD, BAD), List.of(BAD_TOTAL), List.of("Structuring", "Invented indicator"), List.of(
                new ClaimCheck(GOOD.describe(), true, ""),
                new ClaimCheck(BAD.describe(), false, "no such transaction in the ledger"),
                new ClaimCheck(BAD_TOTAL.describe(), false, "the cited transactions sum to 9500.00 CAD"),
                new ClaimCheck("Indicator: Structuring", true, ""),
                new ClaimCheck("Indicator: Invented indicator", false, "search_indicators did not return it")));

        var problems = report("TX-1 and TX-9 total $41,500.00. Matches the Invented indicator.", "Escalated.")
                .finalisationProblems(f);

        assertEquals(3, problems.size(), problems.toString());
        assertTrue(problems.get(0).contains(BAD.describe()) && problems.get(0).contains("no such transaction"));
        assertTrue(problems.get(1).contains(BAD_TOTAL.describe()));
        assertTrue(problems.get(2).contains("Invented indicator"));
        assertEquals(List.of(), report("TX-1 is a cash deposit just under the threshold.", "Escalated.")
                .finalisationProblems(f));
    }

    @Test
    void aReportNeedsAVerifiedFinding() {
        assertEquals(List.of("The case has no verified finding"), report("g", "a").finalisationProblems(null));
        var unchecked = new AgentFinding(Verdict.ESCALATE, 0.9, List.of(), List.of(GOOD), List.of(), "n", false, null);
        assertEquals(List.of("The case has no verified finding"), report("g", "a").finalisationProblems(unchecked));
    }

    @Test
    void lockingMakesTheReportFinalAndItCannotBeEditedAfter() {
        var r = report("g", "a");
        assertEquals(ReportStatus.DRAFT, r.status());

        r.lock(Instant.parse("2026-10-27T12:00:00Z"));

        assertEquals(ReportStatus.FINAL, r.status());
        assertEquals(Instant.parse("2026-10-27T12:00:00Z"), r.finalisedAt());
        assertThrows(IllegalStateException.class, () -> r.withText("new", "a"));
        assertThrows(IllegalStateException.class, () -> r.lock(Instant.now()));
    }

    @Test
    void editingKeepsTheStructuredFields() {
        var edited = report("g", "a").withText("new grounds", "new actions");

        assertEquals("new grounds", edited.groundsForSuspicion());
        assertEquals("new actions", edited.actionsTaken());
        assertEquals(List.of(T1), edited.transactions());
        assertEquals("C1", edited.subjectAccountId());
        assertEquals(ReportStatus.DRAFT, edited.status());
    }
}
