package tracewise.agent.tools;

import java.util.List;
import tracewise.actions.ActionCommand;
import tracewise.actions.CloseCaseCommand;
import tracewise.actions.CommandDispatcher;
import tracewise.llm.ToolParameter;

public class ProposeCloseTool extends ActionTool {

    public static final String NAME = "propose_close";

    public ProposeCloseTool(CommandDispatcher dispatcher, ActionScope scope) {
        super(dispatcher, scope);
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    protected String description() {
        return "Propose closing a case as a false alarm. Only proposes: a human analyst approves or rejects it.";
    }

    @Override
    protected List<ToolParameter> parameters() {
        return List.of(caseId());
    }

    @Override
    protected ActionCommand createCommand(ToolArguments args) {
        return new CloseCaseCommand(args.string("case_id"));
    }
}
