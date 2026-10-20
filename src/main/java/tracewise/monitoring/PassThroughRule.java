package tracewise.monitoring;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import tracewise.domain.Account;
import tracewise.domain.DateRange;
import tracewise.domain.Transaction;
import tracewise.persistence.Ledger;

/** R4: at least $10,000 received and 90% or more of it sent onward within 24 hours. */
public class PassThroughRule extends WindowedRule {

    public PassThroughRule() {
        super(new RuleConfig(true, Map.of("min_incoming", new BigDecimal("10000"), "ratio", new BigDecimal("0.9"),
                "window_hours", new BigDecimal("24"))));
    }

    @Override
    public String id() {
        return "R4";
    }

    @Override
    public RuleKind kind() {
        return RuleKind.SUSPICION;
    }

    @Override
    protected void check(RuleConfig candidate) {
        if (candidate.parameter("ratio").compareTo(BigDecimal.ONE) > 0) {
            throw new InvalidConfigurationException("R4: ratio must be at most 1");
        }
    }

    @Override
    protected List<Transaction> candidates(Ledger ledger, Account account) {
        return ledger.transactionsFor(account.id(), DateRange.ALL);
    }

    @Override
    protected boolean isMatch(Account account, List<Transaction> window) {
        BigDecimal in = BigDecimal.ZERO;
        BigDecimal outAfterIn = BigDecimal.ZERO;
        for (Transaction t : window) {
            if (t.receiverAccountId().equals(account.id())) {
                in = in.add(t.amountCad());
            } else if (in.signum() > 0) {
                outAfterIn = outAfterIn.add(t.amountCad());
            }
        }
        return in.compareTo(parameter("min_incoming")) >= 0
                && outAfterIn.compareTo(in.multiply(parameter("ratio"))) >= 0;
    }

    @Override
    protected BigDecimal total(Account account, List<Transaction> window) {
        return Rules.total(window.stream().filter(t -> t.receiverAccountId().equals(account.id())).toList());
    }

    @Override
    protected Duration window() {
        return Duration.ofHours(parameter("window_hours").longValueExact());
    }
}
