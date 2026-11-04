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
            Style st = style();
            var now = java.time.Instant.now();
            var rows = alerts.stream().map(a -> List.of(st.bold(a.id()), Style.rule(a.ruleId()), a.accountId(),
                    st.risk(a.riskScore()), String.valueOf(a.priority()), a.status().name().replace('_', ' '),
                    Style.money(a.totalCad()), String.valueOf(a.transactionIds().size()),
                    Style.age(java.time.Duration.between(a.createdAt(), now)))).toList();
            out().println(st.table(List.of("ID", "RULE", "ACCOUNT", "RISK", "PRI", "STATUS", "TOTAL CAD", "TX", "AGE"),
                    "llllrlrrr", rows));
            String next = alerts.stream().filter(a -> a.status() == AlertStatus.NEW).findFirst()
                    .map(a -> ". Next: investigate " + a.id() + ", or triage for all of them").orElse("");
            out().println(st.dim("  " + alerts.size() + (alerts.size() == 1 ? " alert" : " alerts") + ", highest risk first" + next));
        }
    }
}
