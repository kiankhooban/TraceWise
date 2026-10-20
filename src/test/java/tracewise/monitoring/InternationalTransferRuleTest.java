package tracewise.monitoring;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import tracewise.domain.PaymentChannel;

class InternationalTransferRuleTest {

    final InternationalTransferRule rule = new InternationalTransferRule();

    @Test
    void largeIncomingOrAggregatedOutgoingTransfersMatch() {
        assertEquals(RuleKind.THRESHOLD, rule.kind());
        assertEquals(1, rule.evaluate(new InMemoryLedger()
                .add("EXT-US-1", "A", "12106.55", PaymentChannel.WIRE, "US", 1)).size());
        var out = new InMemoryLedger();
        for (int i = 0; i < 4; i++) {
            out.add("A", "EXT-AE-" + i, "2600", PaymentChannel.WIRE, "AE", 1 + 5 * i);
        }
        var matches = rule.evaluate(out);
        assertEquals(1, matches.size());
        assertEquals(4, matches.get(0).transactions().size());
    }

    @Test
    void directionsAreAggregatedSeparately() {
        assertTrue(rule.evaluate(new InMemoryLedger()
                .add("EXT-US-1", "A", "6000", PaymentChannel.WIRE, "US", 1)
                .add("A", "EXT-US-2", "6000", PaymentChannel.WIRE, "US", 2)).isEmpty());
    }

    @Test
    void domesticTransfersAndCashDoNotCount() {
        assertTrue(rule.evaluate(new InMemoryLedger().wire("B", "A", "15000", 1)
                .add("CASH", "A", "15000", PaymentChannel.CASH, "US", 2)).isEmpty());
    }
}
