package tracewise.persistence;

import java.util.List;
import java.util.Optional;
import tracewise.domain.Alert;
import tracewise.domain.AlertStatus;
import tracewise.monitoring.AlertFilter;
import tracewise.monitoring.RuleMatch;

public interface AlertRepository {

    /** Stores a new alert and assigns its id. */
    void save(Alert alert);

    /** Matching alerts, highest risk first. */
    List<Alert> find(AlertFilter filter);

    /** True if an alert from the same rule, for the same account and sharing a transaction, is not closed. */
    boolean hasOpenAlert(RuleMatch match);

    Optional<Alert> findById(String id);

    void updateStatus(String id, AlertStatus status);
}
