package tracewise.persistence;

import java.util.Optional;
import tracewise.monitoring.RuleConfig;

public interface RuleConfigRepository {

    void save(String ruleId, RuleConfig config);

    /** The saved configuration, or empty if the rule still uses its defaults. */
    Optional<RuleConfig> load(String ruleId);
}
