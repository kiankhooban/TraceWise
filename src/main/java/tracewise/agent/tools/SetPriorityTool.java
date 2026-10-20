package tracewise.agent.tools;

import java.util.List;
import tracewise.actions.ActionCommand;
import tracewise.actions.CommandDispatcher;
import tracewise.actions.SetPriorityCommand;
import tracewise.llm.ToolParameter;

public class SetPriorityTool extends ActionTool {

    public SetPriorityTool(CommandDispatcher dispatcher) {
        super(dispatcher);
    }

    @Override
    public String name() {
        return "set_priority";
    }

    @Override
    protected String description() {
        return "Set an alert's priority in the queue. Runs immediately.";
    }

    @Override
    protected List<ToolParameter> parameters() {
        return List.of(new ToolParameter("alert_id", ToolParameter.Type.STRING, "The alert, for example ALR-00001", true),
                new ToolParameter("priority", ToolParameter.Type.INTEGER, "1 high, 2 medium, 3 low", true));
    }

    @Override
    protected ActionCommand createCommand(ToolArguments args) {
        return new SetPriorityCommand(args.string("alert_id"), args.integer("priority", 0));
    }
}
