package tracewise.monitoring;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import tracewise.domain.Transaction;

class LargeCashRuleTest {

    final LargeCashRule rule = new LargeCashRule();

    @Test
    void isAThresholdRule() {
        assertEquals("R1", rule.id());
        assertEquals(RuleKind.THRESHOLD, rule.kind());
    }

    @Test
    void oneLargeDepositMatches() {
        var matches = rule.evaluate(new InMemoryLedger().deposit("A", "15000.00", 1));
        assertEquals(1, matches.size());
        assertEquals("A", matches.get(0).accountId());
        assertEquals(new BigDecimal("15000.00"), matches.get(0).totalCad());
    }

    @Test
    void depositsWithin24HoursAreAggregated() {
        var matches = rule.evaluate(new InMemoryLedger().deposit("A", "6000.00", 1).deposit("A", "4000.00", 20));
        assertEquals(1, matches.size());
        assertEquals(new BigDecimal("10000.00"), matches.get(0).totalCad());
        assertEquals(List.of("t1", "t2"), matches.get(0).transactions().stream().map(Transaction::id).toList());
    }

    @Test
    void belowThresholdOrOutsideTheWindowDoesNotMatch() {
        assertTrue(rule.evaluate(new InMemoryLedger().deposit("A", "9999.99", 1)).isEmpty());
        assertTrue(rule.evaluate(new InMemoryLedger().deposit("A", "6000", 1).deposit("A", "5000", 26)).isEmpty());
    }

    @Test
    void withdrawalsAndWiresDoNotCount() {
        assertTrue(rule.evaluate(new InMemoryLedger()
                .add("A", Transaction.CASH, "15000", tracewise.domain.PaymentChannel.CASH, "CA", 1)
                .wire("B", "A", "15000", 2)).isEmpty());
    }
}
