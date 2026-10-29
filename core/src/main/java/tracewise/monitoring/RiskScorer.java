package tracewise.monitoring;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import tracewise.domain.Account;

/**
 * Risk score 0-100 for a new alert: the rule's weight, plus up to 30 for the amount (15 per
 * $10,000), plus 10 for each additional rule the account triggered (up to 20), plus 20 if the
 * account is on the watchlist.
 */
public class RiskScorer {

    private static final Map<String, Integer> RULE_WEIGHTS = Map.of("R3", 30, "R4", 35, "R5", 25, "R6", 25);
    private static final BigDecimal TEN_THOUSAND = new BigDecimal("10000");

    public int score(RuleMatch match, Account account, int rulesTriggered) {
        int amount = Math.min(30, match.totalCad().multiply(BigDecimal.valueOf(15)).divide(TEN_THOUSAND, RoundingMode.DOWN).intValue());
        int otherRules = Math.min(20, 10 * Math.max(0, rulesTriggered - 1));
        int watchlist = account.watchlisted() ? 20 : 0;
        return Math.min(100, RULE_WEIGHTS.getOrDefault(match.ruleId(), 25) + amount + otherRules + watchlist);
    }
}
