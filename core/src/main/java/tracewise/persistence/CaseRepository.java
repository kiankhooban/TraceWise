package tracewise.persistence;

import java.util.List;
import java.util.Optional;
import tracewise.domain.Case;

public interface CaseRepository {

    /** Inserts or updates the case; findings already stored are kept, new ones are added. */
    void save(Case c);

    Optional<Case> findById(String id);

    Optional<Case> findByAlert(String alertId);

    List<Case> findAll();
}
