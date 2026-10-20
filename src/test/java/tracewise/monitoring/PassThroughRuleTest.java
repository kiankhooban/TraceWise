package tracewise.monitoring;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.HashMap;
import org.junit.jupiter.api.Test;

class PassThroughRuleTest {

    final PassThroughRule rule = new PassThroughRule();

    @Test
    void mostOfALargeIncomingAmountSentOnWithinADayMatches() {
        var matches = rule.evaluate(new InMemoryLedger().wire("EXT-P", "P", "48000", 0).wire("P", "EXT-Q", "45500", 5));
        assertEquals(1, matches.size());
        assertEquals(new BigDecimal("48000"), matches.get(0).totalCad(), "the amount passed through, not in plus out");
    }

    @Test
    void tooLittleSentOnTooSmallTooLateOrSentFirstDoesNotMatch() {
        assertTrue(rule.evaluate(new InMemoryLedger().wire("EXT-P", "P", "48000", 0).wire("P", "EXT-Q", "40000", 5)).isEmpty());
        assertTrue(rule.evaluate(new InMemoryLedger().wire("EXT-P", "P", "9000", 0).wire("P", "EXT-Q", "9000", 1)).isEmpty());
        assertTrue(rule.evaluate(new InMemoryLedger().wire("EXT-P", "P", "48000", 0).wire("P", "EXT-Q", "45500", 30)).isEmpty());
        assertTrue(rule.evaluate(new InMemoryLedger().wire("P", "EXT-Q", "45500", 0).wire("EXT-P", "P", "48000", 5)).isEmpty());
    }

    @Test
    void aRatioAboveOneIsRejected() {
        var params = new HashMap<>(rule.config().parameters());
        params.put("ratio", new BigDecimal("1.5"));
        assertThrows(InvalidConfigurationException.class, () -> rule.configure(new RuleConfig(true, params)));
    }
}
