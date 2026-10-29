package tracewise.agent.tools;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import tracewise.domain.Account;
import tracewise.domain.DateRange;
import tracewise.domain.Transaction;
import tracewise.persistence.Ledger;

/**
 * Decorator over the ledger the read tools use: while an investigation runs, the data stops at the alert's last
 * flagged transaction, as it stood for the bank on that day; otherwise (the assistant) everything is visible. Without
 * it the agent judged whether activity was routine using months that came after the alert.
 */
public class AsOfLedger implements Ledger {

    private final Ledger ledger;
    private final Supplier<Optional<Instant>> cutoff;

    /** cutoff gives the last moment the running investigation may see, or empty when none is running. */
    public AsOfLedger(Ledger ledger, Supplier<Optional<Instant>> cutoff) {
        this.ledger = ledger;
        this.cutoff = cutoff;
    }

    @Override
    public List<Account> accounts() {
        return ledger.accounts();
    }

    @Override
    public List<Transaction> transactionsFor(String accountId, DateRange range) {
        return cutoff.get().map(c -> ledger.transactionsFor(accountId,
                new DateRange(range.from(), earlier(range.to(), c.plusMillis(1))))).orElseGet(
                        () -> ledger.transactionsFor(accountId, range));
    }

    @Override
    public Optional<Transaction> findTransaction(String id) {
        Optional<Instant> c = cutoff.get();
        return ledger.findTransaction(id).filter(t -> c.isEmpty() || !t.timestamp().isAfter(c.get()));
    }

    @Override
    public Optional<Account> findAccount(String id) {
        return ledger.findAccount(id);
    }

    @Override
    public Optional<DateRange> coverage() {
        Optional<Instant> c = cutoff.get();
        return ledger.coverage().map(r -> c.isEmpty() ? r : new DateRange(r.from(), earlier(r.to(), c.get())));
    }

    private static Instant earlier(Instant a, Instant b) {
        return a.isBefore(b) ? a : b;
    }
}
