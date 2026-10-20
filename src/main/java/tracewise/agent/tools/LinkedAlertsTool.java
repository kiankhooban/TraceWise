package tracewise.agent.tools;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import tracewise.domain.Alert;
import tracewise.domain.AlertStatus;
import tracewise.domain.DateRange;
import tracewise.domain.Transaction;
import tracewise.llm.ToolParameter;
import tracewise.llm.ToolSchema;
import tracewise.monitoring.AlertFilter;
import tracewise.persistence.AlertRepository;
import tracewise.persistence.CaseRepository;
import tracewise.persistence.Ledger;

/** Read tool: open alerts on an account and on everyone it exchanged money with, with their cases. */
public class LinkedAlertsTool implements AgentTool {

    private final Ledger ledger;
    private final AlertRepository alerts;
    private final CaseRepository cases;

    public LinkedAlertsTool(Ledger ledger, AlertRepository alerts, CaseRepository cases) {
        this.ledger = ledger;
        this.alerts = alerts;
        this.cases = cases;
    }

    @Override
    public String name() {
        return "linked_alerts";
    }

    @Override
    public ToolSchema schema() {
        return new ToolSchema(name(), "Alerts that are not closed on an account and on its direct counterparties, "
                + "with the case each belongs to.", List.of(
                new ToolParameter("account_id", ToolParameter.Type.STRING, "The account to look around", true)));
    }

    @Override
    public ToolResult execute(ToolArguments args) {
        String account = args.string("account_id");
        var related = new LinkedHashSet<String>(List.of(account));
        for (Transaction t : ledger.transactionsFor(account, DateRange.ALL)) {
            related.add(t.senderAccountId());
            related.add(t.receiverAccountId());
        }
        var rows = alerts.find(AlertFilter.ALL).stream()
                .filter(a -> a.status() != AlertStatus.CLOSED && related.contains(a.accountId()))
                .sorted(java.util.Comparator.comparing(Alert::id)).map(this::row).toList();
        var result = new LinkedHashMap<String, Object>();
        result.put("account_id", account);
        result.put("alerts", rows);
        return ToolResult.json(result);
    }

    private Map<String, Object> row(Alert a) {
        var row = new LinkedHashMap<String, Object>();
        row.put("alert_id", a.id());
        row.put("rule", a.ruleId());
        row.put("account_id", a.accountId());
        row.put("status", a.status().name());
        row.put("total_cad", a.totalCad());
        row.put("case_id", cases.findByAlert(a.id()).map(c -> c.id()).orElse(null));
        return row;
    }
}
