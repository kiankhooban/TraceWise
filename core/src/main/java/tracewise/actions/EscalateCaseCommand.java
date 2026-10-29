package tracewise.actions;

import java.util.Optional;
import tracewise.domain.Case;

/** Sends a case to the reporting officer (final). The agent can only propose this. */
public record EscalateCaseCommand(String caseId) implements ActionCommand {

    @Override
    public void validate(CommandContext ctx) {
        ctx.activeCase(caseId);
    }

    @Override
    public void execute(CommandContext ctx) {
        Case c = ctx.activeCase(caseId);
        c.escalate();
        ctx.cases().save(c);
        ctx.memory().remember(c);
    }

    @Override
    public String describe() {
        return "Escalate case " + caseId + " for a suspicious transaction report";
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
