package tracewise.persistence;

import java.util.List;
import java.util.Optional;
import tracewise.domain.Account;
import tracewise.domain.DateRange;
import tracewise.domain.Transaction;

/** Read-only view of the transactions: what rules, tools and the claim verifier query. */
public interface Ledger {

    List<Account> accounts();

    /** Transactions the account sent or received in the range, oldest first. */
    List<Transaction> transactionsFor(String accountId, DateRange range);

    Optional<Transaction> findTransaction(String id);

    /** The earliest and the latest transaction time in the data (from and to are both timestamps that exist). */
    Optional<DateRange> coverage();

    /** One account by id: a key lookup, unlike scanning accounts(). */
    Optional<Account> findAccount(String id);
}
