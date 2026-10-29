package tracewise.monitoring;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class FanRulesTest {

    @Test
    void fiveOrMoreDistinctSendersInAWeekIsFanIn() {
        var ledger = new InMemoryLedger();
        for (int i = 1; i <= 6; i++) {
            ledger.wire("EXT-S" + i, "F", "2000", 20 * i);
        }
        var matches = new FanInRule().evaluate(ledger);
        assertEquals(1, matches.size());
        assertEquals("F", matches.get(0).accountId());
    }

    @Test
    void fanInNeedsDistinctSendersWithinTheWindow() {
        var four = new InMemoryLedger();
        var repeated = new InMemoryLedger();
        var spread = new InMemoryLedger();
        for (int i = 1; i <= 6; i++) {
            four.wire("EXT-S" + Math.min(i, 4), "F", "2000", i);
            repeated.wire("EXT-S1", "F", "2000", i);
            spread.wire("EXT-S" + i, "F", "2000", 50 * i);
        }
        assertTrue(new FanInRule().evaluate(four).isEmpty());
        assertTrue(new FanInRule().evaluate(repeated).isEmpty());
        assertTrue(new FanInRule().evaluate(spread).isEmpty());
    }

    @Test
    void fiveOrMoreDistinctRecipientsInAWeekIsFanOut() {
        var ledger = new InMemoryLedger();
        for (int i = 1; i <= 7; i++) {
            ledger.wire("O", "EXT-R" + i, "1500", 10 * i);
        }
        assertEquals(1, new FanOutRule().evaluate(ledger).size());
        assertTrue(new FanOutRule().evaluate(new InMemoryLedger().wire("O", "EXT-R1", "1", 1)).isEmpty());
    }
}
