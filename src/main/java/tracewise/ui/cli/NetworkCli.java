package tracewise.ui.cli;

import java.time.LocalDate;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import tracewise.domain.DateRange;
import tracewise.network.AccountNode;
import tracewise.network.FlowEdge;
import tracewise.network.MoneyFlowGraph;

@Command(name = "network", description = "Who an account sends money to and receives money from, up to 3 hops (F09).")
public class NetworkCli extends CliCommandBase {

    @Parameters(index = "0", description = "The account, for example C1037.")
    String accountId;

    @Option(names = "--hops", defaultValue = "2", description = "How far to follow the money: 1 to 3 (default: ${DEFAULT-VALUE}).")
    int hops;

    @Option(names = "--from", description = "First day included, YYYY-MM-DD (UTC).")
    LocalDate from;

    @Option(names = "--to", description = "Last day included, YYYY-MM-DD (UTC).")
    LocalDate to;

    @Option(names = "--case", description = "Mark the accounts this case's latest investigation examined.")
    String caseId;

    @Override
    protected Object execute() {
        return facade().buildNetwork(accountId, hops, DateRange.ofDays(from, to), caseId);
    }

    @Override
    protected void print(Object result) {
        if (json) {
            super.print(result);
            return;
        }
        var g = (MoneyFlowGraph) result;
        out().println("Accounts:");
        for (AccountNode n : g.nodes()) {
            out().println("  " + n.accountId() + "  hop " + n.hop() + (n.alerted() ? "  [alerted]" : "")
                    + (n.examined() ? "  [examined by the agent]" : ""));
        }
        if (g.edges().isEmpty()) {
            out().println("No transfers in this period.");
            return;
        }
        out().println("Transfers:");
        for (FlowEdge e : g.edges()) {
            out().println("  " + e.from() + " -> " + e.to() + "  " + String.format("%,.2f", e.totalCad()) + " CAD  ("
                    + e.transferCount() + (e.transferCount() == 1 ? " transfer)" : " transfers)"));
        }
        if (g.omittedCount() > 0) {
            out().println(g.omittedCount() + " smaller counterparties omitted.");
        }
    }
}
