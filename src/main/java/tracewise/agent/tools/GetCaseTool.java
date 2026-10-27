package tracewise.agent.tools;

import java.util.LinkedHashMap;
import java.util.List;
import tracewise.agent.AgentFinding;
import tracewise.domain.Case;
import tracewise.llm.ToolParameter;
import tracewise.llm.ToolSchema;
import tracewise.persistence.AlertRepository;
import tracewise.persistence.CaseRepository;

/** Read tool (F10): one case with its alerts, follow-up tasks and the reasoning of its latest finding. */
public class GetCaseTool implements AgentTool {

    public static final String NAME = "get_case";

    private final CaseRepository cases;
    private final AlertRepository alerts;

    public GetCaseTool(CaseRepository cases, AlertRepository alerts) {
        this.cases = cases;
        this.alerts = alerts;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public ToolSchema schema() {
        return new ToolSchema(name(), "One case: status, alerts, follow-up tasks, the analysts' notes, and its latest finding (verdict, "
                + "confidence, indicators, narrative and verification).", List.of(
                new ToolParameter("case_id", ToolParameter.Type.STRING, "The case, for example CAS-00001", true)));
    }

    @Override
    public ToolResult execute(ToolArguments args) {
        String id = args.string("case_id");
        Case c = cases.findById(id).orElseThrow(() -> new IllegalArgumentException("Unknown case '" + id + "'"));
        var result = new LinkedHashMap<String, Object>();
        result.put("case_id", c.id());
        result.put("status", c.status().name());
        result.put("alerts", c.alertIds().stream().flatMap(a -> alerts.findById(a).stream()).map(a -> {
            var alert = new LinkedHashMap<String, Object>();
            alert.put("alert_id", a.id());
            alert.put("rule", a.ruleId());
            alert.put("account_id", a.accountId());
            alert.put("total_cad", a.totalCad().toPlainString());
            alert.put("transaction_ids", a.transactionIds());
            return alert;
        }).toList());
        result.put("tasks", c.tasks().stream().map(t -> t.description() + (t.done() ? " (done)" : "")).toList());
        result.put("notes", c.notes().stream().map(n -> {
            var note = new LinkedHashMap<String, Object>();
            note.put("author", n.author());
            note.put("at", n.createdAt().toString());
            note.put("text", n.text());
            return note;
        }).toList());
        AgentFinding f = c.latestFinding();
        if (f == null) {
            result.put("latest_finding", null);
        } else {
            var finding = new LinkedHashMap<String, Object>();
            finding.put("verdict", f.verdict().name());
            finding.put("confidence", f.confidence());
            finding.put("indicators", f.indicators());
            finding.put("narrative", f.narrative());
            finding.put("verification", f.verification() == null ? null : f.verification().summary());
            result.put("latest_finding", finding);
        }
        return ToolResult.json(result);
    }
}
