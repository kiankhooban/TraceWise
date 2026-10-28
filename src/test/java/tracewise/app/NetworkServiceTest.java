package tracewise.app;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tracewise.agent.AgentFinding;
import tracewise.agent.AgentTrace;
import tracewise.agent.TraceStep;
import tracewise.agent.Verdict;
import tracewise.cases.state.CaseStatus;
import tracewise.domain.Alert;
import tracewise.domain.AlertStatus;
import tracewise.domain.Case;
import tracewise.domain.DateRange;
import tracewise.domain.PaymentChannel;
import tracewise.domain.Transaction;
import tracewise.llm.ModelConfig;
import tracewise.network.AccountNode;
import tracewise.network.FlowEdge;
import tracewise.network.MoneyFlowGraph;
import tracewise.persistence.Database;
import tracewise.persistence.SqliteAlertRepository;
import tracewise.persistence.SqliteCaseRepository;
import tracewise.persistence.SqliteTransactionRepository;

/** F09: the network around an account, hop by hop, with alerted and examined accounts marked. */
class NetworkServiceTest {

    static final Instant T0 = Instant.parse("2026-09-01T00:00:00Z");

    @TempDir Path dir;
    Database db;
    SqliteTransactionRepository ledger;
    SqliteAlertRepository alerts;
    SqliteCaseRepository cases;
    NetworkService service;
    final List<Transaction> txs = new ArrayList<>();

    void tx(String from, String to, String cad, int day) {
        txs.add(new Transaction("t" + (txs.size() + 1), T0.plus(Duration.ofDays(day)), from, to, new BigDecimal(cad),
                new BigDecimal(cad), "CAD", PaymentChannel.WIRE, "CA", null));
    }

    @BeforeEach
    void open() {
        db = Database.open(dir.resolve("n.db"));
        ledger = new SqliteTransactionRepository(db);
        alerts = new SqliteAlertRepository(db);
        cases = new SqliteCaseRepository(db);
        service = new NetworkService(ledger, alerts, cases);
        tx("C3", "C1", "1200.50", 0);
        tx("C1", "C2", "4000", 1);
        tx("C1", "C2", "3000", 2);
        tx("C1", "EXT-9", "8000", 3);
        tx("C2", "C5", "6500", 4);
        tx("C5", "C6", "100", 5);
        tx("C1", "C4", "50", 40);
    }

    @AfterEach
    void close() {
        db.close();
    }

    MoneyFlowGraph build(String account, int hops, DateRange range, String caseId) {
        ledger.saveAll(txs);
        txs.clear();
        return service.build(account, hops, range, caseId);
    }

    static Map<String, AccountNode> nodes(MoneyFlowGraph g) {
        return g.nodes().stream().collect(Collectors.toMap(AccountNode::accountId, Function.identity()));
    }

    static FlowEdge edge(MoneyFlowGraph g, String from, String to) {
        return g.edges().stream().filter(e -> e.from().equals(from) && e.to().equals(to)).findFirst().orElseThrow(
                () -> new AssertionError("no edge " + from + " -> " + to + " in " + g.edges()));
    }

    @Test
    void oneHopShowsWhoSentMoneyAndWhoReceivedItWithTotals() {
        var g = build("C1", 1, DateRange.ALL, null);

        assertEquals(java.util.Set.of("C1", "C2", "C3", "C4", "EXT-9"), nodes(g).keySet());
        assertEquals(0, nodes(g).get("C1").hop());
        assertEquals(1, nodes(g).get("C2").hop());
        var toC2 = edge(g, "C1", "C2");
        assertEquals(0, new BigDecimal("7000").compareTo(toC2.totalCad()));
        assertEquals(2, toC2.transferCount());
        assertEquals(0, new BigDecimal("1200.50").compareTo(edge(g, "C3", "C1").totalCad()));
        assertEquals(4, g.edges().size());
        assertEquals(0, g.omittedCount());
    }

    @Test
    void moreHopsFollowTheMoneyButNeverIntoOtherInstitutions() {
        var g = build("C1", 2, DateRange.ALL, null);

        assertEquals(2, nodes(g).get("C5").hop());
        assertEquals(0, new BigDecimal("6500").compareTo(edge(g, "C2", "C5").totalCad()));
        assertFalse(nodes(g).containsKey("C6"), "C6 is three hops away");
        assertTrue(build("C1", 3, DateRange.ALL, null).nodes().stream().anyMatch(n -> n.accountId().equals("C6")));
    }

    @Test
    void theDateRangeLimitsTheTransfers() {
        var g = build("C1", 1, new DateRange(T0, T0.plus(Duration.ofDays(30))), null);

        assertFalse(nodes(g).containsKey("C4"), "the C4 transfer is on day 40");
    }

    @Test
    void alertedAndExaminedAccountsAreMarked() {
        alerts.save(new Alert(null, "R4", "C3", List.of(), new BigDecimal("1200.50"), 50, 2, AlertStatus.IN_CASE, T0));
        var trace = new AgentTrace("ALR-00001", ModelConfig.of("replay", "claude-haiku-5-5", false), BigDecimal.ONE);
        trace.record(new TraceStep("ALR-00001", 1, "account_history", "{\"account_id\":\"C2\"}", "{}", false, 1));
        cases.save(new Case("CAS-00001", T0, List.of("ALR-00001"), CaseStatus.UNDER_INVESTIGATION, List.of(
                new AgentFinding(Verdict.ESCALATE, 0.9, List.of(), List.of(), List.of(), "n", false, trace))));

        var g = build("C1", 1, DateRange.ALL, "CAS-00001");

        assertTrue(nodes(g).get("C3").alerted());
        assertFalse(nodes(g).get("C2").alerted());
        assertTrue(nodes(g).get("C2").examined());
        assertFalse(nodes(g).get("C3").examined());
        assertFalse(build("C1", 1, DateRange.ALL, null).nodes().stream().anyMatch(AccountNode::examined));
    }

    @Test
    void aBusyAccountKeepsThe50LargestCounterpartiesAndCountsTheRest() {
        for (int i = 1; i <= 60; i++) {
            tx("C1", "R" + i, String.valueOf(1000 + i), 6);
        }

        var g = build("C1", 1, DateRange.ALL, null);

        assertEquals(51, g.nodes().size(), "the account itself and its 50 largest counterparties");
        assertEquals(14, g.omittedCount(), "60 + 4 counterparties, 50 kept");
        assertTrue(nodes(g).containsKey("R60") && !nodes(g).containsKey("C4"), "the smallest totals are dropped");
        assertTrue(g.edges().stream().allMatch(e -> nodes(g).containsKey(e.from()) && nodes(g).containsKey(e.to())));
    }

    @Test
    void anAccountWithNoTransfersInTheRangeIsASingleNode() {
        var g = build("C1", 2, new DateRange(T0.plus(Duration.ofDays(100)), T0.plus(Duration.ofDays(101))), null);

        assertEquals(List.of("C1"), g.nodes().stream().map(AccountNode::accountId).toList());
        assertTrue(g.edges().isEmpty());
    }

    @Test
    void unknownAccountsAndHopsOutside1To3AreRefused() {
        assertThrows(IllegalArgumentException.class, () -> build("NOPE", 1, DateRange.ALL, null));
        assertThrows(IllegalArgumentException.class, () -> build("C1", 0, DateRange.ALL, null));
        assertThrows(IllegalArgumentException.class, () -> build("C1", 4, DateRange.ALL, null));
    }
}
