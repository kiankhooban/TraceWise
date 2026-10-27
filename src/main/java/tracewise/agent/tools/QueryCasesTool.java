package tracewise.agent.tools;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import tracewise.cases.state.CaseStatus;
import tracewise.domain.Alert;
import tracewise.domain.Case;
import tracewise.llm.ToolParameter;
import tracewise.llm.ToolSchema;
import tracewise.persistence.AlertRepository;
import tracewise.persistence.CaseRepository;

/**
 * Read tool (F10): cases matching a structured filter. The schema rejects unknown fields; execute() rejects
 * values outside the allowed ones, so the agent is told what to correct.
 */
public class QueryCasesTool implements AgentTool {

    public static final String NAME = "query_cases";
    static final int MAX_ROWS = 50;
    private static final Set<String> RULES = Set.of("R1", "R2", "R3", "R4", "R5", "R6");

    private final CaseRepository cases;
    private final AlertRepository alerts;

    public QueryCasesTool(CaseRepository cases, AlertRepository alerts) {
        this.cases = cases;
        this.alerts = alerts;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public ToolSchema schema() {
        return new ToolSchema(name(), "Cases matching a filter, each with its status, alerts, total CAD and latest "
                + "verdict. Every field is optional; leave out what the request does not name.", List.of(
                new ToolParameter("status", ToolParameter.Type.STRING, "One of " + Arrays.toString(CaseStatus.values()), false),
                new ToolParameter("rule", ToolParameter.Type.STRING, "Monitoring rule of one of the case's alerts: R1 large "
                        + "cash, R2 international transfer, R3 structuring, R4 pass-through, R5 fan-in, R6 fan-out", false),
                new ToolParameter("account_id", ToolParameter.Type.STRING, "Account of one of the case's alerts", false),
                new ToolParameter("min_total_cad", ToolParameter.Type.NUMBER, "Smallest total of the case's alerts, CAD", false)));
    }

    @Override
    public ToolResult execute(ToolArguments args) {
        CaseStatus status = args.has("status") ? status(args.string("status")) : null;
        String rule = args.has("rule") ? args.string("rule") : null;
        if (rule != null && !RULES.contains(rule)) {
            throw new IllegalArgumentException("'rule' must be one of R1 to R6, not '" + rule + "'");
        }
        String account = args.has("account_id") ? args.string("account_id") : null;
        BigDecimal minTotal = args.has("min_total_cad") ? args.number("min_total_cad") : null;
        if (minTotal != null && minTotal.signum() < 0) {
            throw new IllegalArgumentException("'min_total_cad' must not be negative");
        }
        var rows = new ArrayList<Map<String, Object>>();
        for (Case c : cases.findAll().stream().sorted(Comparator.comparing(Case::id)).toList()) {
            List<Alert> caseAlerts = c.alertIds().stream().flatMap(id -> alerts.findById(id).stream()).toList();
            BigDecimal total = caseAlerts.stream().map(Alert::totalCad).reduce(BigDecimal.ZERO, BigDecimal::add);
            if ((status == null || c.status() == status)
                    && (rule == null || caseAlerts.stream().anyMatch(a -> a.ruleId().equals(rule)))
                    && (account == null || caseAlerts.stream().anyMatch(a -> a.accountId().equals(account)))
                    && (minTotal == null || total.compareTo(minTotal) >= 0)) {
                rows.add(row(c, caseAlerts, total));
            }
        }
        var result = new LinkedHashMap<String, Object>();
        result.put("count", rows.size());
        result.put("cases", rows.subList(0, Math.min(rows.size(), MAX_ROWS)));
        if (rows.size() > MAX_ROWS) {
            result.put("message", "Only the first " + MAX_ROWS + " are listed; narrow the filter to see the rest.");
        }
        return ToolResult.json(result);
    }

    private static CaseStatus status(String value) {
        try {
            return CaseStatus.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("'status' must be one of " + Arrays.toString(CaseStatus.values())
                    + ", not '" + value + "'");
        }
    }

    private static Map<String, Object> row(Case c, List<Alert> caseAlerts, BigDecimal total) {
        var row = new LinkedHashMap<String, Object>();
        row.put("case_id", c.id());
        row.put("status", c.status().name());
        row.put("total_cad", total.toPlainString());
        row.put("latest_verdict", c.latestFinding() == null ? null : c.latestFinding().verdict().name());
        row.put("alerts", caseAlerts.stream().map(a -> {
            var alert = new LinkedHashMap<String, Object>();
            alert.put("alert_id", a.id());
            alert.put("rule", a.ruleId());
            alert.put("account_id", a.accountId());
            alert.put("total_cad", a.totalCad().toPlainString());
            return alert;
        }).toList());
        return row;
    }
}
