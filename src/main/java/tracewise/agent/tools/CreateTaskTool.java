package tracewise.agent.tools;

import java.util.List;
import tracewise.actions.ActionCommand;
import tracewise.actions.CommandDispatcher;
import tracewise.actions.CreateTaskCommand;
import tracewise.llm.ToolParameter;

public class CreateTaskTool extends ActionTool {

    public static final String NAME = "create_task";

    public CreateTaskTool(CommandDispatcher dispatcher, ActionScope scope) {
        super(dispatcher, scope);
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    protected String description() {
        return "Create a follow-up task for the analyst on a case, for evidence the tools cannot give "
                + "(for example customer identity or the source of cash). Runs immediately.";
    }

    @Override
    protected List<ToolParameter> parameters() {
        return List.of(caseId(), new ToolParameter("description", ToolParameter.Type.STRING, "What to do", true));
    }

    @Override
    protected ActionCommand createCommand(ToolArguments args) {
        return new CreateTaskCommand(args.string("case_id"), args.string("description"));
    }
}
