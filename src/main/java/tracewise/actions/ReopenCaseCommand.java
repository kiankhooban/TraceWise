package tracewise.actions;

import java.util.Optional;
import tracewise.domain.AlertStatus;
import tracewise.domain.Case;

/** F08: returns a closed case to Open (State pattern: only ClosedState allows it); its alerts are open again. */
public record ReopenCaseCommand(String caseId, String reason) implements ActionCommand {

    @Override
    public void validate(CommandContext ctx) {
        ctx.caseFor(caseId);
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A reason is required to reopen a case");
        }
    }

    @Override
    public void execute(CommandContext ctx) {
        Case c = ctx.caseFor(caseId);
        c.reopen(reason);
        ctx.cases().save(c);
        c.alertIds().forEach(id -> ctx.alerts().updateStatus(id, AlertStatus.IN_CASE));
        ctx.memory().remember(c); // no longer a decided case
    }

    @Override
    public String describe() {
        return "Reopen case " + caseId + ": " + reason.strip();
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
