package tracewise.agent.tools;

import java.util.Arrays;
import java.util.List;
import tracewise.actions.ActionCommand;
import tracewise.actions.CommandDispatcher;
import tracewise.actions.LinkAlertsCommand;
import tracewise.llm.ToolParameter;

public class LinkAlertsTool extends ActionTool {

    public static final String NAME = "link_alerts";

    public LinkAlertsTool(CommandDispatcher dispatcher, ActionScope scope) {
        super(dispatcher, scope);
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    protected String description() {
        return "Add related alerts (for example on a counterparty in the same scheme) to a case. Runs immediately.";
    }

    @Override
    protected List<ToolParameter> parameters() {
        return List.of(caseId(), new ToolParameter("alert_ids", ToolParameter.Type.STRING,
                "Comma-separated alert ids, for example ALR-00002,ALR-00005", true));
    }

    @Override
    protected ActionCommand createCommand(ToolArguments args) {
        return new LinkAlertsCommand(args.string("case_id"),
                Arrays.stream(args.string("alert_ids").split(",")).map(String::strip).filter(s -> !s.isEmpty()).toList());
    }
}
