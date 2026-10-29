package tracewise.actions;

import java.util.Optional;
import tracewise.domain.Case;
import tracewise.domain.FollowUpTask;

public record CreateTaskCommand(String caseId, String description) implements ActionCommand {

    @Override
    public void validate(CommandContext ctx) {
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("A task needs a description");
        }
        ctx.activeCase(caseId);
    }

    @Override
    public void execute(CommandContext ctx) {
        Case c = ctx.activeCase(caseId);
        c.addTask(new FollowUpTask(description, false));
        ctx.cases().save(c);
    }

    @Override
    public String describe() {
        return "Create a task on " + caseId + ": " + description;
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
