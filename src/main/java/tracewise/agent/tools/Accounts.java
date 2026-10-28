package tracewise.agent.tools;

import tracewise.domain.Transaction;
import tracewise.persistence.Ledger;

/** Shared check for the read tools. */
final class Accounts {

    private Accounts() {
    }

    static boolean exists(Ledger ledger, String id) {
        return Transaction.isOutsideBank(id) || ledger.findAccount(id).isPresent();
    }
}
