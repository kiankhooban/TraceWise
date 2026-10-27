package tracewise.agent.tools;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tracewise.domain.Alert;
import tracewise.domain.AlertStatus;
import tracewise.llm.ToolParameter;
import tracewise.llm.ToolSchema;
import tracewise.monitoring.AlertFilter;
import tracewise.persistence.AlertRepository;
import tracewise.persistence.CaseRepository;

/** Read tool (F10): alerts matching a filter, including those not in any case. Values are validated. */
public class QueryAlertsTool implements AgentTool {

    public static final String NAME = "query_alerts";

    private final AlertRepository alerts;
    private final CaseRepository cases;

    public QueryAlertsTool(AlertRepository alerts, CaseRepository cases) {
        this.alerts = alerts;
        this.cases = cases;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public ToolSchema schema() {
        return new ToolSchema(name(), "Alerts matching a filter, each with its rule, account, status, total CAD, risk "
                + "score, flagged transaction ids and case. Every field is optional.", List.of(
                new ToolParameter("status", ToolParameter.Type.STRING, "NEW (not investigated yet), IN_CASE or CLOSED", false),
                new ToolParameter("rule", ToolParameter.Type.STRING, QueryCasesTool.RULE_HELP, false),
                new ToolParameter("account_id", ToolParameter.Type.STRING, "The alert's account", false),
                new ToolParameter("min_total_cad", ToolParameter.Type.NUMBER, "Smallest alert total, CAD", false)));
    }

    @Override
    public ToolResult execute(ToolArguments args) {
        AlertStatus status = args.has("status") ? status(args.string("status")) : null;
        String rule = args.has("rule") ? QueryCasesTool.rule(args.string("rule")) : null;
        BigDecimal minTotal = args.has("min_total_cad") ? args.number("min_total_cad") : null;
        if (minTotal != null && minTotal.signum() < 0) {
            throw new IllegalArgumentException("'min_total_cad' must not be negative");
        }
        String account = args.has("account_id") ? args.string("account_id") : null;
        var rows = alerts.find(new AlertFilter(rule, status, minTotal)).stream()
                .filter(a -> account == null || a.accountId().equals(account))
                .sorted(Comparator.comparing(Alert::id)).map(this::row).toList();
        var result = new LinkedHashMap<String, Object>();
        result.put("count", rows.size());
        result.put("alerts", rows.subList(0, Math.min(rows.size(), QueryCasesTool.MAX_ROWS)));
        if (rows.size() > QueryCasesTool.MAX_ROWS) {
            result.put("message", "Only the first " + QueryCasesTool.MAX_ROWS + " are listed; narrow the filter.");
        }
        return ToolResult.json(result);
    }

    private static AlertStatus status(String value) {
        try {
            return AlertStatus.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("'status' must be NEW, IN_CASE or CLOSED, not '" + value + "'");
        }
    }

    private Map<String, Object> row(Alert a) {
        var row = new LinkedHashMap<String, Object>();
        row.put("alert_id", a.id());
        row.put("rule", a.ruleId());
        row.put("account_id", a.accountId());
        row.put("status", a.status().name());
        row.put("total_cad", a.totalCad().toPlainString());
        row.put("risk_score", a.riskScore());
        row.put("transaction_ids", a.transactionIds());
        row.put("case_id", cases.findByAlert(a.id()).map(c -> c.id()).orElse(null));
        return row;
    }
}
