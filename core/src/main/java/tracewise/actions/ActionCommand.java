package tracewise.actions;

import java.util.Optional;

/**
 * Command: one action as an object, so it can be validated, checked by the permission policy, held for
 * approval and run later, and audited the same way whether the agent or a person asked for it.
 * validate() throws IllegalArgumentException or IllegalStateException naming what is wrong.
 */
public interface ActionCommand {

    void validate(CommandContext ctx);

    void execute(CommandContext ctx);

    String describe();

    /** The case this action changes, if it changes one. */
    Optional<String> targetCaseId();

    /** The alert, account or case the action is about, for the audit trail. */
    String target();
}
