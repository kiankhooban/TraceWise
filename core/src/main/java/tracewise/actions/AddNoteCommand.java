package tracewise.actions;

import java.time.Instant;
import java.util.Optional;
import tracewise.domain.Case;
import tracewise.domain.CaseNote;

/** F08: an analyst's note on a case, in any state. */
public record AddNoteCommand(String caseId, String text, String author) implements ActionCommand {

    @Override
    public void validate(CommandContext ctx) {
        ctx.caseFor(caseId);
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("A note needs some text");
        }
    }

    @Override
    public void execute(CommandContext ctx) {
        Case c = ctx.caseFor(caseId);
        c.addNote(new CaseNote(text.strip(), author, Instant.now()));
        ctx.cases().save(c);
    }

    @Override
    public String describe() {
        return "Add a note to case " + caseId + ": " + text.strip();
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
