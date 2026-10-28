package tracewise.app;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import tracewise.agent.AgentFinding;
import tracewise.domain.Account;
import tracewise.domain.Alert;
import tracewise.domain.DateRange;
import tracewise.domain.Transaction;
import tracewise.monitoring.AlertFilter;
import tracewise.network.AccountNode;
import tracewise.network.FlowEdge;
import tracewise.network.MoneyFlowGraph;
import tracewise.persistence.AlertRepository;
import tracewise.persistence.CaseRepository;
import tracewise.persistence.Ledger;

/**
 * F09: builds the money-flow graph around an account, hop by hop. Read-only and deterministic: the agent's trace only
 * decides which accounts are marked as examined.
 */
public class NetworkService {

    /** The most counterparties drawn (the design's maxNodes); the largest totals are kept. */
    static final int MAX_NODES = 50;

    private final Ledger ledger;
    private final AlertRepository alerts;
    private final CaseRepository cases;

    public NetworkService(Ledger ledger, AlertRepository alerts, CaseRepository cases) {
        this.ledger = ledger;
        this.alerts = alerts;
        this.cases = cases;
    }

    /** caseId (may be null) names the case whose latest investigation marks the examined accounts. */
    public MoneyFlowGraph build(String accountId, int hops, DateRange range, String caseId) {
        if (hops < 1 || hops > 3) {
            throw new IllegalArgumentException("Hops must be 1, 2 or 3, got " + hops);
        }
        // shortcut: scans every account (as the read tools do); add Ledger.findAccount if large imports make it slow
        if (!Transaction.isOutsideBank(accountId) && ledger.accounts().stream().map(Account::id).noneMatch(accountId::equals)) {
            throw new IllegalArgumentException("Unknown account " + accountId);
        }
        var hopOf = new LinkedHashMap<String, Integer>(Map.of(accountId, 0));
        var edges = new LinkedHashMap<List<String>, FlowEdge>();
        var seenTransactions = new HashSet<String>();
        var candidates = new HashSet<String>();
        List<String> frontier = List.of(accountId);
        for (int hop = 1; hop <= hops && !frontier.isEmpty(); hop++) {
            var reached = new LinkedHashMap<String, BigDecimal>();
            for (String account : frontier) {
                if (Transaction.isOutsideBank(account)) {
                    continue; // the cash counter and other institutions have no ledger of their own here
                }
                for (Transaction t : ledger.transactionsFor(account, range)) {
                    if (seenTransactions.add(t.id())) {
                        edges.merge(List.of(t.senderAccountId(), t.receiverAccountId()),
                                new FlowEdge(t.senderAccountId(), t.receiverAccountId(), t.amountCad(), 1),
                                (a, b) -> new FlowEdge(a.from(), a.to(), a.totalCad().add(b.totalCad()),
                                        a.transferCount() + 1));
                    }
                    String other = account.equals(t.senderAccountId()) ? t.receiverAccountId() : t.senderAccountId();
                    if (!hopOf.containsKey(other)) {
                        reached.merge(other, t.amountCad(), BigDecimal::add);
                    }
                }
            }
            candidates.addAll(reached.keySet());
            int room = MAX_NODES - (hopOf.size() - 1);
            // largest totals first; ties by id so the same data always draws the same graph
            List<String> kept = reached.entrySet().stream()
                    .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed().thenComparing(Map.Entry::getKey))
                    .limit(Math.max(0, room)).map(Map.Entry::getKey).toList();
            for (String account : kept) {
                hopOf.put(account, hop);
            }
            frontier = kept;
        }
        Set<String> alerted = alerts.find(AlertFilter.ALL).stream().map(Alert::accountId).collect(Collectors.toSet());
        Set<String> examined = caseId == null ? Set.of() : cases.findById(caseId).map(c -> c.latestFinding())
                .map(AgentFinding::trace).map(t -> t.examinedAccounts()).orElse(Set.of());
        var nodes = new ArrayList<AccountNode>();
        hopOf.forEach((account, hop) -> nodes.add(new AccountNode(account, hop, alerted.contains(account),
                examined.contains(account))));
        List<FlowEdge> drawn = edges.values().stream()
                .filter(e -> hopOf.containsKey(e.from()) && hopOf.containsKey(e.to())).toList();
        return new MoneyFlowGraph(accountId, nodes, drawn, candidates.size() - (hopOf.size() - 1));
    }
}
