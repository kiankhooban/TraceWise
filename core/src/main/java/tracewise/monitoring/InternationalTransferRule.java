package tracewise.monitoring;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import tracewise.domain.Account;
import tracewise.domain.DateRange;
import tracewise.domain.PaymentChannel;
import tracewise.domain.Transaction;
import tracewise.persistence.Ledger;

/**
 * R2: international electronic funds transfers of $10,000 or more within 24 hours. Incoming and
 * outgoing are aggregated separately, as FINTRAC reports them separately.
 */
public class InternationalTransferRule extends WindowedRule {

    private static final Set<PaymentChannel> ELECTRONIC = Set.of(PaymentChannel.WIRE, PaymentChannel.ACH);

    public InternationalTransferRule() {
        super(new RuleConfig(true, Map.of("threshold", new BigDecimal("10000"), "window_hours", new BigDecimal("24"))));
    }

    @Override
    public String id() {
        return "R2";
    }

    @Override
    public RuleKind kind() {
        return RuleKind.THRESHOLD;
    }

    @Override
    protected List<Transaction> candidates(Ledger ledger, Account account) {
        return ledger.transactionsFor(account.id(), DateRange.ALL).stream()
                .filter(t -> t.isInternational() && ELECTRONIC.contains(t.channel())).toList();
    }

    @Override
    protected boolean isMatch(Account account, List<Transaction> window) {
        return total(account, window).compareTo(parameter("threshold")) >= 0;
    }

    /** The larger of the incoming and outgoing totals: the direction that reaches the threshold. */
    @Override
    protected BigDecimal total(Account account, List<Transaction> window) {
        BigDecimal in = Rules.total(window.stream().filter(t -> t.receiverAccountId().equals(account.id())).toList());
        BigDecimal out = Rules.total(window.stream().filter(t -> t.senderAccountId().equals(account.id())).toList());
        return in.max(out);
    }

    @Override
    protected Duration window() {
        return Duration.ofHours(parameter("window_hours").longValueExact());
    }
}
