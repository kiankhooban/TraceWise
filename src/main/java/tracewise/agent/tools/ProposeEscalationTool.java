package tracewise.agent.tools;

import java.util.List;
import tracewise.actions.ActionCommand;
import tracewise.actions.CommandDispatcher;
import tracewise.actions.EscalateCaseCommand;
import tracewise.llm.ToolParameter;

public class ProposeEscalationTool extends ActionTool {

    public static final String NAME = "propose_escalation";

    public ProposeEscalationTool(CommandDispatcher dispatcher, ActionScope scope) {
        super(dispatcher, scope);
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    protected String description() {
        return "Propose escalating a case for a suspicious transaction report. Only proposes: a human analyst "
                + "approves or rejects it.";
    }

    @Override
    protected List<ToolParameter> parameters() {
        return List.of(caseId());
    }

    @Override
    protected ActionCommand createCommand(ToolArguments args) {
        return new EscalateCaseCommand(args.string("case_id"));
    }
}
