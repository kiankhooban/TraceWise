package tracewise.persistence;

import java.util.List;
import java.util.Optional;
import tracewise.domain.Account;
import tracewise.domain.Transaction;

public interface TransactionRepository extends Ledger {

    void saveAll(List<Transaction> transactions);

    boolean exists(String id);

    /** Creates the account if it is new; an existing account keeps its watchlist flag. */
    void saveAccount(Account account);

    Optional<Account> findAccount(String id);

    void setWatchlisted(String accountId, boolean watchlisted);
}
