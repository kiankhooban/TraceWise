package tracewise.actions;

import java.util.Set;
import tracewise.domain.Actor;

/**
 * Decides in code, never in the prompt, which actions wait for a human. Analysts and supervisors act
 * directly; anyone else (the agent) may act alone only for the listed low-risk actions, so closing,
 * escalating, and any action added later wait for approval unless deliberately listed here.
 */
public class PermissionPolicy {

    private static final Set<Class<? extends ActionCommand>> AGENT_MAY_DO_ALONE = Set.of(SetPriorityCommand.class,
            LinkAlertsCommand.class, AddToWatchlistCommand.class, CreateTaskCommand.class);

    public PermissionTier tierFor(ActionCommand command, Actor actor) {
        return actor.isHuman() || AGENT_MAY_DO_ALONE.contains(command.getClass())
                ? PermissionTier.AUTONOMOUS : PermissionTier.REQUIRES_APPROVAL;
    }
}
