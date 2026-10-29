package tracewise.ui.cli;

import java.math.BigDecimal;
import java.util.List;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import tracewise.domain.Alert;
import tracewise.domain.AlertStatus;
import tracewise.monitoring.AlertFilter;

@Command(name = "alerts", description = "The alert queue.", subcommands = AlertsCli.ListAlerts.class)
public class AlertsCli {

    @Command(name = "list", description = "List alerts, highest risk first.")
    static class ListAlerts extends CliCommandBase {

        @Option(names = "--rule", description = "Only alerts from this rule, for example R3.")
        String rule;

        @Option(names = "--status", description = "NEW, IN_CASE or CLOSED.")
        AlertStatus status;

        @Option(names = "--min-amount", description = "Only alerts with at least this total in CAD.")
        BigDecimal minAmount;

        @Override
        protected Object execute() {
            return facade().listAlerts(new AlertFilter(rule, status, minAmount));
        }

        @Override
        @SuppressWarnings("unchecked")
        protected void print(Object result) {
            if (json) {
                super.print(result);
                return;
            }
            var alerts = (List<Alert>) result;
            if (alerts.isEmpty()) {
                out().println("No alerts");
                return;
            }
            out().printf("%-10s %-4s %-10s %4s %8s %-8s %12s %s%n", "ID", "RULE", "ACCOUNT", "RISK", "PRIORITY",
                    "STATUS", "TOTAL (CAD)", "TRANSACTIONS");
            for (Alert a : alerts) {
                out().printf("%-10s %-4s %-10s %4d %8d %-8s %12s %d%n", a.id(), a.ruleId(), a.accountId(),
                        a.riskScore(), a.priority(), a.status(), a.totalCad().toPlainString(),
                        a.transactionIds().size());
            }
        }
    }
}
