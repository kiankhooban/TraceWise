package tracewise.actions;

import java.util.Optional;
import tracewise.domain.AlertStatus;
import tracewise.domain.Case;

/** Closes a case as a false alarm, and its alerts with it. The agent can only propose this. */
public record CloseCaseCommand(String caseId) implements ActionCommand {

    @Override
    public void validate(CommandContext ctx) {
        ctx.activeCase(caseId);
    }

    @Override
    public void execute(CommandContext ctx) {
        Case c = ctx.activeCase(caseId);
        c.close();
        ctx.cases().save(c);
        c.alertIds().forEach(id -> ctx.alerts().updateStatus(id, AlertStatus.CLOSED));
    }

    @Override
    public String describe() {
        return "Close case " + caseId + " as a false alarm";
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
