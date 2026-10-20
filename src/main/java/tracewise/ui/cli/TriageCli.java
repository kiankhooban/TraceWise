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
        AgentEventListener progress = json ? null : new AgentEventListener() {
            @Override
            public void onStep(TraceStep s) {
                out().printf("  [%s] %s%s%n", s.subject(), s.toolName(), s.error() ? " (error)" : "");
            }

            @Override
            public void onFinished(AgentOutcome outcome) {
                out().println(outcome.subject() + ": " + outcome.status());
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
        out().println();
        for (TriageReport.Item i : r.items()) {
            out().printf("%-10s %-10s %-10s %-12s %s%n", i.alertId(), i.caseId() == null ? "-" : i.caseId(), i.status(),
                    i.verdict() == null ? "-" : i.verdict(), i.verification() == null ? i.message() : i.verification());
        }
        out().println(r.summary());
    }
}
