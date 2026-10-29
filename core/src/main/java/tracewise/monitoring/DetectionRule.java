package tracewise.monitoring;

import java.util.List;
import tracewise.persistence.Ledger;

/** Strategy: one monitoring rule. MonitoringService runs every enabled rule the same way. */
public interface DetectionRule {

    String id();

    RuleKind kind();

    List<RuleMatch> evaluate(Ledger ledger);

    /** Applies a new configuration, or throws InvalidConfigurationException and keeps the current one. */
    void configure(RuleConfig config);

    RuleConfig config();
}
