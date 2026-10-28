package tracewise.network;

import java.util.List;

/**
 * F09: the accounts around rootAccountId and the money between them. omittedCount is how many counterparties were
 * left out to keep the graph readable (the largest totals are kept).
 */
public record MoneyFlowGraph(String rootAccountId, List<AccountNode> nodes, List<FlowEdge> edges, int omittedCount) {

    public MoneyFlowGraph {
        nodes = List.copyOf(nodes);
        edges = List.copyOf(edges);
    }
}
