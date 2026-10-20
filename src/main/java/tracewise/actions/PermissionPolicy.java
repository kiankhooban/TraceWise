package tracewise.actions;

import java.util.Set;
import tracewise.domain.Actor;

/**
 * Decides in code, never in the prompt, which actions wait for a human: closing and escalating a case,
 * when anyone other than an analyst or supervisor asks.
 */
public class PermissionPolicy {

    private static final Set<Class<? extends ActionCommand>> NEEDS_APPROVAL =
            Set.of(CloseCaseCommand.class, EscalateCaseCommand.class);

    public PermissionTier tierFor(ActionCommand command, Actor actor) {
        return !actor.isHuman() && NEEDS_APPROVAL.contains(command.getClass())
                ? PermissionTier.REQUIRES_APPROVAL : PermissionTier.AUTONOMOUS;
    }
}
