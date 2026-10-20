package tracewise.agent.tools;

import java.util.ArrayList;
import java.util.List;
import tracewise.actions.ActionCommand;
import tracewise.actions.CommandDispatcher;
import tracewise.actions.DispatchResult;
import tracewise.domain.Actor;
import tracewise.llm.ToolParameter;
import tracewise.llm.ToolSchema;

/**
 * Factory Method: every action tool submits its command the same way (as the agent, with its
 * justification, through the dispatcher); each subclass only decides which command to create.
 * Whether the command runs now or waits for approval is the dispatcher's decision, not the tool's.
 */
public abstract class ActionTool implements AgentTool {

    private final CommandDispatcher dispatcher;

    protected ActionTool(CommandDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    protected abstract ActionCommand createCommand(ToolArguments args);

    protected abstract String description();

    protected abstract List<ToolParameter> parameters();

    @Override
    public final ToolSchema schema() {
        var params = new ArrayList<>(parameters());
        params.add(new ToolParameter("justification", ToolParameter.Type.STRING,
                "Why, citing the evidence; shown to the analyst and kept in the audit trail", true));
        return new ToolSchema(name(), description(), params);
    }

    @Override
    public final ToolResult execute(ToolArguments args) {
        DispatchResult result = dispatcher.submit(createCommand(args), Actor.AGENT, args.string("justification"));
        return result.status() == DispatchResult.Status.REJECTED ? ToolResult.error(result.message())
                : ToolResult.ok(result.message());
    }

    static ToolParameter caseId() {
        return new ToolParameter("case_id", ToolParameter.Type.STRING, "The case, for example CAS-00001", true);
    }
}
