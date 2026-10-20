package tracewise.monitoring;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import tracewise.domain.Account;
import tracewise.domain.DateRange;
import tracewise.domain.Transaction;
import tracewise.persistence.Ledger;

/** R5: five or more different senders paying one account within 7 days. */
public class FanInRule extends WindowedRule {

    public FanInRule() {
        super(new RuleConfig(true, Map.of("min_senders", new BigDecimal("5"), "window_days", new BigDecimal("7"))));
    }

    @Override
    public String id() {
        return "R5";
    }

    @Override
    public RuleKind kind() {
        return RuleKind.SUSPICION;
    }

    @Override
    protected List<Transaction> candidates(Ledger ledger, Account account) {
        return ledger.transactionsFor(account.id(), DateRange.ALL).stream()
                .filter(t -> t.receiverAccountId().equals(account.id()) && !t.isCashDeposit()).toList();
    }

    @Override
    protected boolean isMatch(Account account, List<Transaction> window) {
        return window.stream().map(Transaction::senderAccountId).distinct().count()
                >= parameter("min_senders").intValue();
    }

    @Override
    protected Duration window() {
        return Duration.ofDays(parameter("window_days").longValueExact());
    }
}
