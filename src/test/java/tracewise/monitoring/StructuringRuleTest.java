package tracewise.monitoring;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.HashMap;
import org.junit.jupiter.api.Test;

class StructuringRuleTest {

    final StructuringRule rule = new StructuringRule();

    static InMemoryLedger threeInBand(double gapHours) {
        return new InMemoryLedger().deposit("S", "9200", 0).deposit("S", "9600", gapHours)
                .deposit("S", "9900", 2 * gapHours);
    }

    @Test
    void threeDepositsJustUnderTheThresholdWithinAWeekMatch() {
        assertEquals(RuleKind.SUSPICION, rule.kind());
        var matches = rule.evaluate(threeInBand(30));
        assertEquals(1, matches.size());
        assertEquals(3, matches.get(0).transactions().size());
    }

    @Test
    void depositsBelowTheBandOrSpreadOverMoreThanAWeekDoNotMatch() {
        assertTrue(rule.evaluate(new InMemoryLedger().deposit("S", "7999.99", 0).deposit("S", "7999.99", 30)
                .deposit("S", "7999.99", 60)).isEmpty());
        assertTrue(rule.evaluate(threeInBand(85)).isEmpty());
    }

    @Test
    void configurationChangesTheRule() {
        var params = new HashMap<>(rule.config().parameters());
        params.put("min_count", new BigDecimal("4"));
        rule.configure(new RuleConfig(true, params));
        assertTrue(rule.evaluate(threeInBand(30)).isEmpty());
    }

    @Test
    void invalidConfigurationIsRejectedAndThePreviousOneStays() {
        var before = rule.config();
        var params = new HashMap<>(before.parameters());
        params.put("lower", new BigDecimal("12000"));
        var e = assertThrows(InvalidConfigurationException.class, () -> rule.configure(new RuleConfig(true, params)));
        assertEquals("R3: lower must be below upper", e.getMessage());

        params.put("lower", new BigDecimal("-1"));
        assertEquals("parameter lower must be positive",
                assertThrows(InvalidConfigurationException.class, () -> rule.configure(new RuleConfig(true, params))).getMessage());

        var unknown = new HashMap<>(before.parameters());
        unknown.put("colour", BigDecimal.ONE);
        assertEquals("R3: unknown parameter colour",
                assertThrows(InvalidConfigurationException.class, () -> rule.configure(new RuleConfig(true, unknown))).getMessage());
        assertEquals(before, rule.config());
    }

    @Test
    void windowsAndCountsMustBeWholeNumbers() {
        var params = new HashMap<>(rule.config().parameters());
        params.put("window_days", new BigDecimal("7.5"));
        assertEquals("R3: window_days must be a whole number",
                assertThrows(InvalidConfigurationException.class, () -> rule.configure(new RuleConfig(true, params))).getMessage());
    }
}
