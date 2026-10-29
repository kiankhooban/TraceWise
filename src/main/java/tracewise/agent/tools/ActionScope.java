package tracewise.agent.tools;

import tracewise.actions.ActionCommand;
import tracewise.actions.SetPriorityCommand;
import tracewise.persistence.CaseRepository;

/**
 * Limits the agent's actions to the case it is investigating: the case itself and the alerts in it.
 * The model's arguments are untrusted, so a run on one alert must not change or block another case.
 */
public class ActionScope {

    private final CaseRepository cases;
    private volatile String caseId;

    public ActionScope(CaseRepository cases) {
        this.cases = cases;
    }

    public void begin(String caseId) {
        this.caseId = caseId;
    }

    public void end() {
        caseId = null;
    }

    /** The case under investigation, or empty when no investigation is running. */
    public java.util.Optional<String> currentCase() {
        return java.util.Optional.ofNullable(caseId);
    }

    /** Why the agent may not submit this command now, or null if it may. */
    String problemWith(ActionCommand command) {
        String current = caseId;
        if (current == null) {
            return "Not done: no investigation is running, so the agent cannot act";
        }
        if (command.targetCaseId().isPresent() && !command.targetCaseId().get().equals(current)) {
            return "Not done: you may act only on case " + current + ", the case under investigation";
        }
        if (command instanceof SetPriorityCommand p
                && cases.findById(current).map(c -> !c.alertIds().contains(p.alertId())).orElse(true)) {
            return "Not done: you may set the priority only of alerts in case " + current;
        }
        return null;
    }
}
