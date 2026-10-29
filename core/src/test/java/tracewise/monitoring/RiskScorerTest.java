package tracewise.monitoring;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import tracewise.domain.Account;

class RiskScorerTest {

    final RiskScorer scorer = new RiskScorer();

    static RuleMatch match(String rule, String total) {
        return new RuleMatch(rule, RuleKind.SUSPICION, "A", List.of(), new BigDecimal(total), Instant.EPOCH, Instant.EPOCH);
    }

    @Test
    void ruleWeightPlusAmountRelativeToTenThousand() {
        assertEquals(30 + 30, scorer.score(match("R3", "37000"), new Account("A", false), 1));
        assertEquals(25 + 18, scorer.score(match("R5", "12000"), new Account("A", false), 1));
        assertEquals(35 + 15, scorer.score(match("R4", "10000"), new Account("A", false), 1));
    }

    @Test
    void moreRulesAndTheWatchlistRaiseTheScoreCappedAt100() {
        assertEquals(25 + 1 + 10, scorer.score(match("R6", "900"), new Account("A", false), 2));
        assertEquals(25 + 1 + 20 + 20, scorer.score(match("R6", "900"), new Account("A", true), 5));
        assertEquals(100, scorer.score(match("R4", "90000"), new Account("A", true), 4));
    }
}
