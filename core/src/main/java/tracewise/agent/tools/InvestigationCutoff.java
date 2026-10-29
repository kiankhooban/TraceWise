package tracewise.agent.tools;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Where the data an investigation may see ends: the last flagged transaction of the alert under investigation, as a
 * bank investigating that alert would see it. Worked out once per alert; empty when no investigation runs.
 */
public final class InvestigationCutoff implements Supplier<Optional<Instant>> {

    private final ActionScope scope;
    private final Function<String, Optional<Instant>> lastFlagged;
    private volatile Map.Entry<String, Optional<Instant>> cached;

    /** lastFlagged: the time of an alert's last flagged transaction. */
    public InvestigationCutoff(ActionScope scope, Function<String, Optional<Instant>> lastFlagged) {
        this.scope = scope;
        this.lastFlagged = lastFlagged;
    }

    @Override
    public Optional<Instant> get() {
        String alert = scope.currentAlert().orElse(null);
        if (alert == null) {
            return Optional.empty();
        }
        var c = cached;
        if (c != null && c.getKey().equals(alert)) {
            return c.getValue();
        }
        Optional<Instant> value = lastFlagged.apply(alert);
        cached = Map.entry(alert, value);
        return value;
    }
}
