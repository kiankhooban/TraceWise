package tracewise.agent.tools;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tracewise.domain.DateRange;
import tracewise.monitoring.InMemoryLedger;

/**
 * Found in review 10: the agent judged whether activity was routine using months of transactions after the alert,
 * which a bank investigating that alert would not have. During an investigation the read tools see the data as it
 * stood when the alert's last flagged transaction happened.
 */
class AsOfLedgerTest {

    static final Instant T0 = InMemoryLedger.T0;
    final InMemoryLedger ledger = new InMemoryLedger().deposit("C1", "9500", 1).wire("C1", "C2", "4000", 48);

    @Test
    void anInvestigationSeesNothingAfterItsCutoff() {
        Instant cutoff = T0.plus(Duration.ofHours(1));
        var asOf = new AsOfLedger(ledger, () -> Optional.of(cutoff));

        assertEquals(1, asOf.transactionsFor("C1", DateRange.ALL).size());
        assertEquals(cutoff, asOf.coverage().orElseThrow().to());
        String later = ledger.transactionsFor("C1", DateRange.ALL).get(1).id();
        assertTrue(asOf.findTransaction(later).isEmpty());
        assertTrue(asOf.transactionsFor("C2", DateRange.ALL).isEmpty(), "the counterparty's later transfer too");
    }

    @Test
    void withoutAnInvestigationEverythingIsVisible() {
        var asOf = new AsOfLedger(ledger, Optional::empty);

        assertEquals(2, asOf.transactionsFor("C1", DateRange.ALL).size());
        assertEquals(ledger.coverage(), asOf.coverage());
    }
}
