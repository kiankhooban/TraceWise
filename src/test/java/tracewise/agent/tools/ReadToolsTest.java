package tracewise.agent.tools;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import tracewise.domain.PaymentChannel;
import tracewise.domain.Transaction;
import tracewise.llm.ToolCall;
import tracewise.monitoring.InMemoryLedger;

class ReadToolsTest {

    static final ObjectMapper JSON = new ObjectMapper();

    // T0 is 2026-09-01T00:00Z; hours are offsets from it
    final InMemoryLedger ledger = new InMemoryLedger()
            .deposit("C1", "9500", 0)
            .wire("C1", "C2", "4000", 24)
            .wire("C3", "C1", "1200.50", 48)
            .wire("C1", "C2", "3000", 72)
            .add("C1", "EXT-9", "8000", PaymentChannel.WIRE, "AE", 30 * 24)
            .add(new Transaction("labelled", InMemoryLedger.T0.plus(Duration.ofDays(40)), "C1", "C3", new BigDecimal("10"),
                    new BigDecimal("10"), "CAD", PaymentChannel.ACH, "CA", true));
    final ToolRegistry registry = new ToolRegistry();

    {
        registry.register(new AccountHistoryTool(ledger));
        registry.register(new CounterpartiesTool(ledger));
    }

    JsonNode call(String tool, String args) throws Exception {
        var result = registry.execute(new ToolCall("c1", tool, args));
        assertFalse(result.error(), result.content());
        return JSON.readTree(result.content());
    }

    @Test
    void historyListsTransactionsOldestFirstWithTotals() throws Exception {
        var h = call("account_history", "{\"account_id\":\"C1\"}");
        assertEquals(6, h.get("transaction_count").asInt());
        assertEquals("t1", h.get("transactions").get(0).get("id").asText());
        assertEquals("in", h.get("transactions").get(0).get("direction").asText());
        assertEquals("CASH", h.get("transactions").get(0).get("counterparty").asText());
        assertCad("10700.50", h.get("total_in_cad"));
        assertCad("15010", h.get("total_out_cad"));
        assertEquals("AE", h.get("transactions").get(4).get("country").asText());
    }

    /**
     * IBM baseline: without the data's dates the agent read "no activity before the data starts" as a new account;
     * the currency mix and self-transfers let it compare the alert with the account's other activity.
     */
    @Test
    void historyStatesTheDatesTheDataCoversAndTheAccountsCurrenciesAndSelfTransfers() throws Exception {
        ledger.add(new Transaction("self", InMemoryLedger.T0.plus(Duration.ofHours(5)), "C1", "C1", new BigDecimal("500"),
                new BigDecimal("340"), "EUR", PaymentChannel.ACH, "CA", null));

        var h = call("account_history", "{\"account_id\":\"C1\"}");

        assertEquals("2026-09-01 to 2026-10-11", h.get("data_covers").asText());
        assertEquals(6, h.get("currencies").get("CAD").asInt());
        assertEquals(1, h.get("currencies").get("EUR").asInt());
        assertEquals(1, h.get("self_transfers").asInt());
        assertEquals("2026-09-01 to 2026-10-11", call("counterparties", "{\"account_id\":\"C1\"}").get("data_covers")
                .asText());
    }

    @Test
    void historyNeverRevealsTheGroundTruthLabel() throws Exception {
        assertFalse(call("account_history", "{\"account_id\":\"C1\"}").toString().toLowerCase().contains("laundering"));
    }

    @Test
    void historyDateRangeIncludesBothEndDates() throws Exception {
        var h = call("account_history", "{\"account_id\":\"C1\",\"from\":\"2026-09-02\",\"to\":\"2026-09-03\"}");
        assertEquals(2, h.get("transaction_count").asInt());
    }

    @Test
    void historyShowsOnlyTheMostRecentHundred() throws Exception {
        var busy = new InMemoryLedger();
        for (int i = 0; i < 105; i++) {
            busy.wire("B1", "B2", "10", i);
        }
        var result = new AccountHistoryTool(busy).execute(argsFor("{\"account_id\":\"B1\"}"));
        var h = JSON.readTree(result.content());
        assertEquals(105, h.get("transaction_count").asInt());
        assertEquals(100, h.get("transactions").size());
        assertEquals("t6", h.get("transactions").get(0).get("id").asText());
        assertTrue(h.get("note").asText().contains("100 most recent"));
    }

    @Test
    void unknownAccountAndBadDatesAreErrors() {
        assertTrue(registry.execute(new ToolCall("c", "account_history", "{\"account_id\":\"NOPE\"}")).error());
        var bad = registry.execute(new ToolCall("c", "account_history", "{\"account_id\":\"C1\",\"from\":\"Sept 2\"}"));
        assertTrue(bad.error());
        assertTrue(bad.content().contains("YYYY-MM-DD"), bad.content());
    }

    @Test
    void counterpartiesAreAggregatedByTotalLargestFirst() throws Exception {
        var c = call("counterparties", "{\"account_id\":\"C1\"}");
        var first = c.get("counterparties").get(0);
        assertEquals("CASH", first.get("account_id").asText());
        var c2 = c.get("counterparties").get(2);
        assertEquals("C2", c2.get("account_id").asText());
        assertEquals(2, c2.get("sent_count").asInt());
        assertCad("7000", c2.get("sent_total_cad"));
        var c3 = c.get("counterparties").get(3);
        assertEquals("C3", c3.get("account_id").asText());
        assertEquals(1, c3.get("received_count").asInt());
        assertEquals(1, c3.get("sent_count").asInt());
        assertEquals(4, c.get("counterparties").size());
        // replay matches requests byte for byte, so key order must not vary between JVM runs
        assertTrue(registry.execute(new ToolCall("c", "counterparties", "{\"account_id\":\"C1\"}")).content()
                .startsWith("{\"account_id\""));
    }

    static void assertCad(String expected, JsonNode actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual.decimalValue()), expected + " vs " + actual);
    }

    static ToolArguments argsFor(String json) throws Exception {
        return new ToolArguments((com.fasterxml.jackson.databind.node.ObjectNode) JSON.readTree(json));
    }
}
