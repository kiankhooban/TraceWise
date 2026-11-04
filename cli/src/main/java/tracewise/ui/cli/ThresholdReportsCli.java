package tracewise.ui.cli;

import java.util.List;
import picocli.CommandLine.Command;
import tracewise.domain.ThresholdReport;

@Command(name = "threshold-reports", description = "The Threshold Report Register (R1, R2).",
        subcommands = ThresholdReportsCli.ListReports.class)
public class ThresholdReportsCli {

    @Command(name = "list", description = "List register entries in time order.")
    static class ListReports extends CliCommandBase {

        @Override
        protected Object execute() {
            return facade().listThresholdReports();
        }

        @Override
        @SuppressWarnings("unchecked")
        protected void print(Object result) {
            if (json) {
                super.print(result);
                return;
            }
            var reports = (List<ThresholdReport>) result;
            if (reports.isEmpty()) {
                out().println("No threshold reports");
                return;
            }
            Style st = style();
            var rows = reports.stream().map(r -> List.of(Style.rule(r.ruleId()), r.accountId(), Style.money(r.totalCad()),
                    Style.time(r.windowStart()) + " to " + Style.time(r.windowEnd()),
                    String.valueOf(r.transactionIds().size()))).toList();
            out().println(st.table(List.of("RULE", "ACCOUNT", "TOTAL CAD", "WINDOW (UTC)", "TX"), "llrlr", rows));
            out().println(st.dim("  " + reports.size() + " threshold reports, filed separately from the alert queue"));
        }
    }
}
