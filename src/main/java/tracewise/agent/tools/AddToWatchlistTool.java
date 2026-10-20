package tracewise.agent.tools;

import java.util.List;
import tracewise.actions.ActionCommand;
import tracewise.actions.AddToWatchlistCommand;
import tracewise.actions.CommandDispatcher;
import tracewise.llm.ToolParameter;

public class AddToWatchlistTool extends ActionTool {

    public AddToWatchlistTool(CommandDispatcher dispatcher) {
        super(dispatcher);
    }

    @Override
    public String name() {
        return "add_to_watchlist";
    }

    @Override
    protected String description() {
        return "Put an account inside the bank on the watchlist, so it scores higher in future monitoring. "
                + "Runs immediately.";
    }

    @Override
    protected List<ToolParameter> parameters() {
        return List.of(new ToolParameter("account_id", ToolParameter.Type.STRING, "The account", true));
    }

    @Override
    protected ActionCommand createCommand(ToolArguments args) {
        return new AddToWatchlistCommand(args.string("account_id"));
    }
}
