package tracewise.monitoring;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import tracewise.domain.Account;
import tracewise.domain.DateRange;
import tracewise.domain.Transaction;
import tracewise.persistence.Ledger;

/** R3: repeated cash deposits just under the reporting threshold (FINTRAC structuring indicator). */
public class StructuringRule extends WindowedRule {

    public StructuringRule() {
        super(new RuleConfig(true, Map.of("min_count", new BigDecimal("3"), "lower", new BigDecimal("8000"),
                "upper", new BigDecimal("9999.99"), "window_days", new BigDecimal("7"))));
    }

    @Override
    public String id() {
        return "R3";
    }

    @Override
    public RuleKind kind() {
        return RuleKind.SUSPICION;
    }

    @Override
    protected void check(RuleConfig candidate) {
        if (candidate.parameter("lower").compareTo(candidate.parameter("upper")) >= 0) {
            throw new InvalidConfigurationException("R3: lower must be below upper");
        }
    }

    @Override
    protected List<Transaction> candidates(Ledger ledger, Account account) {
        return ledger.transactionsFor(account.id(), DateRange.ALL).stream()
                .filter(t -> t.isCashDeposit() && t.receiverAccountId().equals(account.id()))
                .filter(t -> t.amountCad().compareTo(parameter("lower")) >= 0
                        && t.amountCad().compareTo(parameter("upper")) <= 0)
                .toList();
    }

    @Override
    protected boolean isMatch(Account account, List<Transaction> window) {
        return window.size() >= parameter("min_count").intValue();
    }

    @Override
    protected Duration window() {
        return Duration.ofDays(parameter("window_days").longValueExact());
    }
}
