package tracewise.actions;

import java.util.List;
import java.util.Optional;
import tracewise.domain.Alert;
import tracewise.domain.AlertStatus;
import tracewise.domain.Case;

/** Groups related alerts into one case, so they are investigated and decided together. */
public record LinkAlertsCommand(String caseId, List<String> alertIds) implements ActionCommand {

    public LinkAlertsCommand {
        alertIds = List.copyOf(alertIds);
    }

    @Override
    public void validate(CommandContext ctx) {
        ctx.activeCase(caseId);
        if (alertIds.isEmpty()) {
            throw new IllegalArgumentException("No alerts to link");
        }
        for (String id : alertIds) {
            Alert alert = ctx.alerts().findById(id).orElseThrow(() -> new IllegalArgumentException("No alert " + id));
            if (alert.status() == AlertStatus.CLOSED) {
                throw new IllegalStateException("Alert " + id + " is closed");
            }
            ctx.cases().findByAlert(id).filter(other -> !other.id().equals(caseId)).ifPresent(other -> {
                throw new IllegalStateException("Alert " + id + " already belongs to case " + other.id());
            });
        }
    }

    @Override
    public void execute(CommandContext ctx) {
        Case c = ctx.activeCase(caseId);
        alertIds.forEach(c::linkAlert);
        ctx.cases().save(c);
        alertIds.forEach(id -> ctx.alerts().updateStatus(id, AlertStatus.IN_CASE));
    }

    @Override
    public String describe() {
        return "Link alerts " + String.join(", ", alertIds) + " into case " + caseId;
    }

    @Override
    public Optional<String> targetCaseId() {
        return Optional.of(caseId);
    }

    @Override
    public String target() {
        return caseId;
    }
}
