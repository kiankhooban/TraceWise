package tracewise.monitoring;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import tracewise.domain.Account;
import tracewise.domain.Transaction;
import tracewise.persistence.Ledger;

/**
 * Template Method: evaluate() is the scanning algorithm every rule shares (for each account, take
 * its candidate transactions in time order, slide a window over them, test each window). Rules
 * supply only candidates(), isMatch() and window().
 */
public abstract class WindowedRule implements DetectionRule {

    private RuleConfig config;

    protected WindowedRule(RuleConfig defaults) {
        this.config = defaults;
    }

    @Override
    public final List<RuleMatch> evaluate(Ledger ledger) {
        var matches = new ArrayList<RuleMatch>();
        for (Account account : ledger.accounts()) {
            List<Transaction> candidates = candidates(ledger, account);
            int i = 0;
            while (i < candidates.size()) {
                Instant start = candidates.get(i).timestamp();
                Instant end = start.plus(window());
                int j = i;
                while (j < candidates.size() && candidates.get(j).timestamp().isBefore(end)) {
                    j++;
                }
                List<Transaction> window = candidates.subList(i, j);
                if (isMatch(account, window)) {
                    matches.add(new RuleMatch(id(), kind(), account.id(), List.copyOf(window), Rules.total(window),
                            start, end));
                    i = j; // the matched transactions are reported once, not again in an overlapping window
                } else {
                    i++;
                }
            }
        }
        return matches;
    }

    @Override
    public final void configure(RuleConfig next) {
        next.parameters().keySet().stream().filter(name -> !config.parameters().containsKey(name)).findFirst()
                .ifPresent(name -> {
                    throw new InvalidConfigurationException(id() + ": unknown parameter " + name);
                });
        config.parameters().keySet().stream().filter(name -> !next.parameters().containsKey(name)).findFirst()
                .ifPresent(name -> {
                    throw new InvalidConfigurationException(id() + ": missing parameter " + name);
                });
        next.validate();
        next.parameters().forEach((name, value) -> {
            if (name.matches(".*_(days|hours|count|senders|recipients)") && value.stripTrailingZeros().scale() > 0) {
                throw new InvalidConfigurationException(id() + ": " + name + " must be a whole number");
            }
        });
        check(next);
        config = next;
    }

    @Override
    public RuleConfig config() {
        return config;
    }

    protected BigDecimal parameter(String name) {
        return config.parameter(name);
    }

    /** Rule-specific constraints beyond "all parameters positive"; throw InvalidConfigurationException. */
    protected void check(RuleConfig candidate) {
    }

    /** The account's transactions this rule looks at, oldest first. */
    protected abstract List<Transaction> candidates(Ledger ledger, Account account);

    protected abstract boolean isMatch(Account account, List<Transaction> window);

    protected abstract Duration window();
}
