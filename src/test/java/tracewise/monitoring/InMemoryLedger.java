package tracewise.monitoring;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import tracewise.domain.Account;
import tracewise.domain.DateRange;
import tracewise.domain.PaymentChannel;
import tracewise.domain.Transaction;
import tracewise.persistence.Ledger;

/** Test double: a ledger over a list, with helpers that build transactions hours after a fixed start. */
public class InMemoryLedger implements Ledger {

    public static final Instant T0 = Instant.parse("2026-09-01T00:00:00Z");
    private final List<Transaction> transactions = new ArrayList<>();
    private int next;

    public InMemoryLedger add(String from, String to, String cad, PaymentChannel channel, String country, double hours) {
        transactions.add(new Transaction("t" + (++next), T0.plus(Duration.ofMinutes((long) (hours * 60))), from, to,
                new BigDecimal(cad), new BigDecimal(cad), "CAD", channel, country, null));
        return this;
    }

    public InMemoryLedger add(Transaction t) {
        transactions.add(t);
        return this;
    }

    public InMemoryLedger deposit(String account, String cad, double hours) {
        return add(Transaction.CASH, account, cad, PaymentChannel.CASH, "CA", hours);
    }

    public InMemoryLedger wire(String from, String to, String cad, double hours) {
        return add(from, to, cad, PaymentChannel.WIRE, "CA", hours);
    }

    @Override
    public java.util.Optional<Account> findAccount(String id) {
        return accounts().stream().filter(a -> a.id().equals(id)).findFirst();
    }

    @Override
    public List<Account> accounts() {
        var ids = new TreeSet<String>();
        transactions.forEach(t -> {
            ids.add(t.senderAccountId());
            ids.add(t.receiverAccountId());
        });
        ids.removeIf(Transaction::isOutsideBank);
        return ids.stream().map(id -> new Account(id, false)).toList();
    }

    @Override
    public List<Transaction> transactionsFor(String accountId, DateRange range) {
        return transactions.stream()
                .filter(t -> accountId.equals(t.senderAccountId()) || accountId.equals(t.receiverAccountId()))
                .filter(t -> range.contains(t.timestamp()))
                .sorted(Comparator.comparing(Transaction::timestamp)).toList();
    }

    @Override
    public Optional<Transaction> findTransaction(String id) {
        return transactions.stream().filter(t -> t.id().equals(id)).findFirst();
    }
}
