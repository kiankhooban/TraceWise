package tracewise.persistence;

import java.util.List;
import java.util.Optional;
import tracewise.actions.PendingAction;

public interface ApprovalRepository {

    /** Inserts a new proposal or records the decision on an existing one. */
    void save(PendingAction pending);

    Optional<PendingAction> findById(String id);

    /** Proposals still waiting for a decision, oldest first. */
    List<PendingAction> findPending();

    /** Every proposal, decided or not, oldest first. */
    List<PendingAction> findAll();

    /** The id the next proposal gets, APR-00001 onwards. */
    String nextId();
}
