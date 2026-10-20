package tracewise.monitoring;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import tracewise.domain.Account;
import tracewise.domain.DateRange;
import tracewise.domain.Transaction;
import tracewise.persistence.Ledger;

/** R1: cash received by one customer totalling $10,000 or more within 24 hours (FINTRAC 24-hour rule). */
public class LargeCashRule extends WindowedRule {

    public LargeCashRule() {
        super(new RuleConfig(true, Map.of("threshold", new BigDecimal("10000"), "window_hours", new BigDecimal("24"))));
    }

    @Override
    public String id() {
        return "R1";
    }

    @Override
    public RuleKind kind() {
        return RuleKind.THRESHOLD;
    }

    @Override
    protected List<Transaction> candidates(Ledger ledger, Account account) {
        return ledger.transactionsFor(account.id(), DateRange.ALL).stream()
                .filter(t -> t.isCashDeposit() && t.receiverAccountId().equals(account.id())).toList();
    }

    @Override
    protected boolean isMatch(Account account, List<Transaction> window) {
        return Rules.total(window).compareTo(parameter("threshold")) >= 0;
    }

    @Override
    protected Duration window() {
        return Duration.ofHours(parameter("window_hours").longValueExact());
    }
}
