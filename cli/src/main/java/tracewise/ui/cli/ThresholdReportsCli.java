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
            out().printf("%-4s %-10s %12s %-20s %-20s %s%n", "RULE", "ACCOUNT", "TOTAL (CAD)", "WINDOW START",
                    "WINDOW END", "TRANSACTIONS");
            for (ThresholdReport r : reports) {
                out().printf("%-4s %-10s %12s %-20s %-20s %d%n", r.ruleId(), r.accountId(),
                        r.totalCad().toPlainString(), r.windowStart(), r.windowEnd(), r.transactionIds().size());
            }
        }
    }
}
