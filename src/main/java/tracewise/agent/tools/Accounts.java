package tracewise.agent.tools;

import tracewise.domain.Transaction;
import tracewise.persistence.Ledger;

/** Shared check for the read tools. */
final class Accounts {

    private Accounts() {
    }

    /** The UTC dates the data covers, so the agent does not read the edges of the data as facts about an account. */
    static String coverage(Ledger ledger) {
        return ledger.coverage().map(r -> java.time.LocalDate.ofInstant(r.from(), java.time.ZoneOffset.UTC) + " to "
                + java.time.LocalDate.ofInstant(r.to(), java.time.ZoneOffset.UTC)).orElse("no transactions");
    }

    static boolean exists(Ledger ledger, String id) {
        return Transaction.isOutsideBank(id) || ledger.findAccount(id).isPresent();
    }
}
