package tracewise.actions;

import java.util.Optional;
import tracewise.domain.Alert;
import tracewise.domain.AlertStatus;

public record SetPriorityCommand(String alertId, int priority) implements ActionCommand {

    @Override
    public void validate(CommandContext ctx) {
        if (priority < 1 || priority > 3) {
            throw new IllegalArgumentException("priority must be 1 (high), 2 (medium) or 3 (low), not " + priority);
        }
        Alert alert = ctx.alerts().findById(alertId).orElseThrow(() -> new IllegalArgumentException("No alert " + alertId));
        if (alert.status() == AlertStatus.CLOSED) {
            throw new IllegalStateException("Alert " + alertId + " is closed");
        }
    }

    @Override
    public void execute(CommandContext ctx) {
        ctx.alerts().updatePriority(alertId, priority);
    }

    @Override
    public String describe() {
        return "Set alert " + alertId + " to priority " + priority;
    }

    @Override
    public Optional<String> targetCaseId() {
        return Optional.empty();
    }

    @Override
    public String target() {
        return alertId;
    }
}
