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
        Style st = style();
        var nodes = new java.util.ArrayList<java.util.List<String>>();
        for (AccountNode n : g.nodes()) {
            String marks = String.join(", ", java.util.stream.Stream.of(n.alerted() ? st.bad("alerted") : "",
                    n.examined() ? st.label("examined by the agent") : "").filter(m -> !m.isEmpty()).toList());
            nodes.add(java.util.List.of(n.hop() == 0 ? st.bold(n.accountId()) : n.accountId(), String.valueOf(n.hop()), marks));
        }
        out().println(st.table(java.util.List.of("ACCOUNT", "HOP", "MARK"), "lrl", nodes));
        out().println();
        if (g.edges().isEmpty()) {
            out().println("  No transfers in this period.");
            return;
        }
        var edges = new java.util.ArrayList<java.util.List<String>>();
        for (FlowEdge e : g.edges()) {
            edges.add(java.util.List.of(e.from(), e.to(), Style.money(e.totalCad()), String.valueOf(e.transferCount())));
        }
        out().println(st.table(java.util.List.of("FROM", "TO", "TOTAL CAD", "TRANSFERS"), "llrr", edges));
        if (g.omittedCount() > 0) {
            out().println(st.dim("  " + g.omittedCount() + " smaller counterparties omitted."));
        }
    }
}
