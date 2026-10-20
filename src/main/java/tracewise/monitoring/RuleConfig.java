package tracewise.monitoring;

import java.math.BigDecimal;
import java.util.Map;
import java.util.TreeMap;

/** A rule's settings: whether it runs, and its numeric parameters (thresholds, windows, counts). */
public record RuleConfig(boolean enabled, Map<String, BigDecimal> parameters) {

    public RuleConfig {
        parameters = Map.copyOf(parameters);
    }

    public BigDecimal parameter(String name) {
        return parameters.get(name);
    }

    /** Checks what holds for every rule: all parameters positive. Rule-specific checks are in the rule. */
    public void validate() {
        new TreeMap<>(parameters).forEach((name, value) -> {
            if (value == null || value.signum() <= 0) {
                throw new InvalidConfigurationException("parameter " + name + " must be positive");
            }
        });
    }
}
