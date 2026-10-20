package tracewise.agent.tools;

import tracewise.domain.Transaction;
import tracewise.persistence.Ledger;

/** Shared check for the read tools. */
final class Accounts {

    private Accounts() {
    }

    /** shortcut: scans every account; add Ledger.findAccount if this shows up on large imports. */
    static boolean exists(Ledger ledger, String id) {
        return Transaction.isOutsideBank(id) || ledger.accounts().stream().anyMatch(a -> a.id().equals(id));
    }
}
