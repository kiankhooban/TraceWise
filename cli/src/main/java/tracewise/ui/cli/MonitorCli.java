package tracewise.ui.cli;

import picocli.CommandLine.Command;

@Command(name = "monitor", description = "Run the monitoring rules.", subcommands = MonitorCli.Run.class)
public class MonitorCli {

    @Command(name = "run", description = "Run every enabled rule over the imported transactions.")
    static class Run extends CliCommandBase {
        @Override
        protected Object execute() {
            return facade().runMonitoring();
        }

        @Override
        protected void print(Object result) {
            if (json) {
                super.print(result);
                return;
            }
            var s = (tracewise.monitoring.MonitoringSummary) result;
            Style st = style();
            out().println(st.field("Reports", st.bold(String.valueOf(s.thresholdReports())) + " threshold reports"));
            String byRule = new java.util.TreeMap<>(s.alertsByRule()).entrySet().stream().map(e -> Style.rule(e.getKey()) + " " + e.getValue())
                    .collect(java.util.stream.Collectors.joining(", "));
            out().println(st.field("Alerts", st.bold(String.valueOf(s.newAlerts())) + " new" + (byRule.isEmpty() ? "" : ": " + byRule)));
            s.failedRules().forEach(f -> err().println(errStyle().bad("Rule failed:") + " " + f));
            out().println(st.dim(s.newAlerts() > 0 ? "  Next: alerts list" : "  No new alerts"));
        }
    }
}
