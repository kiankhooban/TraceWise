package tracewise.ui.cli;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import tracewise.agent.AgentEventListener;
import tracewise.agent.AgentOutcome;
import tracewise.agent.TraceStep;
import tracewise.agent.TriageReport;
import tracewise.app.TriageLimits;
import tracewise.monitoring.AlertFilter;

@Command(name = "triage", description = "Let the agent investigate the open alerts, highest risk first, within limits.")
public class TriageCli extends CliCommandBase {

    @Option(names = "--max-alerts", defaultValue = "50", description = "Stop after this many alerts (default: ${DEFAULT-VALUE}).")
    int maxAlerts;

    @Option(names = "--max-cost", defaultValue = "5.00", description = "Stop once this much is spent, in CAD (default: ${DEFAULT-VALUE}).")
    BigDecimal maxCost;

    @Option(names = "--rule", description = "Only alerts from this rule, for example R3.")
    String rule;

    @Override
    protected Object execute() {
        var limits = new TriageLimits(maxAlerts, maxCost, new AlertFilter(rule, null, null));
        Style st = style();
        var steps = new java.util.HashMap<String, Integer>();
        AgentEventListener progress = json ? null : new AgentEventListener() {
            @Override
            public void onStep(TraceStep s) {
                int n = steps.merge(s.subject(), 1, Integer::sum);
                if (st.color) { // a terminal: one line per alert, rewritten in place as the agent works
                    String line = "  " + s.subject() + "  investigating, step " + n + ": " + st.stepText(s);
                    out().print("\r\033[K" + st.cut(line)); // cut, so the line never wraps and \r rewrites all of it
                    out().flush();
                }
            }

            @Override
            public void onFinished(AgentOutcome outcome) {
                int n = steps.getOrDefault(outcome.subject(), 0);
                String status = outcome.status() == tracewise.agent.OutcomeStatus.COMPLETED ? st.ok("done")
                        : st.bad(outcome.status().name());
                out().println((st.color ? "\r\033[K" : "") + "  " + st.bold(outcome.subject()) + "  " + status + ", "
                        + n + (n == 1 ? " step" : " steps"));
            }
        };
        return facade().triage(limits, progress);
    }

    @Override
    protected void print(Object result) {
        var r = (TriageReport) result;
        if (json) {
            var map = new LinkedHashMap<String, Object>();
            map.put("endReason", r.endReason());
            map.put("summary", r.summary());
            map.put("processed", r.processed());
            map.put("failed", r.failed());
            map.put("escalationsProposed", r.escalationsProposed());
            map.put("closesProposed", r.closesProposed());
            map.put("alertsLinked", r.alertsLinked());
            map.put("otherActions", r.otherActions());
            map.put("costCad", r.costCad());
            map.put("durationMs", r.durationMs());
            map.put("items", r.items());
            super.print(map);
            return;
        }
        Style st = style();
        out().println();
        if (!r.items().isEmpty()) {
            var alerts = new java.util.HashMap<String, tracewise.domain.Alert>();
            facade().listAlerts(AlertFilter.ALL).forEach(a -> alerts.put(a.id(), a));
            var rows = new java.util.ArrayList<java.util.List<String>>();
            for (TriageReport.Item i : r.items()) {
                var a = alerts.get(i.alertId());
                var finding = i.caseId() == null || i.verdict() == null ? null : facade().getCase(i.caseId()).latestFinding();
                rows.add(java.util.List.of(st.bold(i.alertId()), a == null ? "" : a.accountId(), a == null ? "" : st.risk(a.riskScore()),
                        i.status() == tracewise.agent.OutcomeStatus.COMPLETED ? "done" : st.bad(i.status().name()),
                        st.verdict(i.verdict()), finding == null ? "" : st.check(finding.verification()),
                        i.caseId() == null ? "" : i.caseId()));
            }
            out().println(st.table(java.util.List.of("ALERT", "ACCOUNT", "RISK", "RUN", "VERDICT", "CHECKED", "CASE"),
                    "lllllll", rows));
            r.items().stream().filter(i -> i.status() != tracewise.agent.OutcomeStatus.COMPLETED && i.message() != null)
                    .forEach(i -> out().println(st.wrap(st.bad(i.alertId()) + " " + i.message(), 2)));
            out().println();
        }
        out().println(st.field("Summary", r.summary()));
        if (r.escalationsProposed() + r.closesProposed() > 0) {
            out().println(st.dim("  Next: approvals list"));
        }
    }
}
