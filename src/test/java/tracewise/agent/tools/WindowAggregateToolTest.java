package tracewise.agent.tools;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import tracewise.domain.PaymentChannel;
import tracewise.llm.ToolCall;
import tracewise.monitoring.InMemoryLedger;

/** The design's "aggregates over time windows": totals per day or week, so busy accounts need no paging. */
class WindowAggregateToolTest {

    static final ObjectMapper JSON = new ObjectMapper();

    // T0 is Tuesday 2026-09-01T00:00Z; hours are offsets from it
    final InMemoryLedger ledger = new InMemoryLedger()
            .deposit("C1", "9500", 1)
            .wire("C3", "C1", "1200.50", 2)
            .wire("C1", "C2", "4000", 3)
            .wire("C1", "C4", "3000", 26)
            .add("C1", "EXT-9", "8000", PaymentChannel.WIRE, "AE", 7 * 24);
    final ToolRegistry registry = new ToolRegistry();

    {
        registry.register(new WindowAggregateTool(ledger));
    }

    JsonNode call(String args) throws Exception {
        var result = registry.execute(new ToolCall("c1", WindowAggregateTool.NAME, args));
        assertFalse(result.error(), result.content());
        return JSON.readTree(result.content());
    }

    static void assertCad(String expected, JsonNode actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual.decimalValue()), actual.toString());
    }

    @Test
    void dailyWindowsTotalMoneyInAndOutWithCounterpartiesAndChannels() throws Exception {
        var r = call("{\"account_id\":\"C1\"}");

        assertEquals("day", r.get("window").asText());
        assertEquals(5, r.get("transaction_count").asInt());
        var days = r.get("windows");
        assertEquals(3, days.size(), "only days with activity are listed");
        var first = days.get(0);
        assertEquals("2026-09-01", first.get("start").asText());
        assertEquals(2, first.get("in_count").asInt());
        assertCad("10700.50", first.get("in_cad"));
        assertEquals(1, first.get("out_count").asInt());
        assertCad("4000", first.get("out_cad"));
        assertEquals(3, first.get("counterparties").asInt());
        assertEquals(1, first.get("channels").get("CASH").asInt());
        assertEquals(2, first.get("channels").get("WIRE").asInt());
        assertEquals("2026-09-08", days.get(2).get("start").asText());
    }

    @Test
    void weeklyWindowsStartOnMonday() throws Exception {
        var weeks = call("{\"account_id\":\"C1\",\"window\":\"week\"}").get("windows");

        assertEquals(2, weeks.size());
        assertEquals("2026-08-31", weeks.get(0).get("start").asText());
        assertEquals(4, weeks.get(0).get("in_count").asInt() + weeks.get(0).get("out_count").asInt());
        assertEquals("2026-09-07", weeks.get(1).get("start").asText());
    }

    @Test
    void theDateRangeAndBadArgumentsAreHandled() throws Exception {
        assertEquals(1, call("{\"account_id\":\"C1\",\"from\":\"2026-09-02\",\"to\":\"2026-09-02\"}")
                .get("transaction_count").asInt());
        assertTrue(registry.execute(new ToolCall("c1", WindowAggregateTool.NAME,
                "{\"account_id\":\"C1\",\"window\":\"month\"}")).error());
        assertTrue(registry.execute(new ToolCall("c1", WindowAggregateTool.NAME, "{\"account_id\":\"NOPE\"}")).error());
    }

    @Test
    void neverRevealsTheGroundTruthLabelOrTransactionRows() throws Exception {
        String text = call("{\"account_id\":\"C1\"}").toString().toLowerCase();
        assertFalse(text.contains("laundering") || text.contains("label"));
        assertFalse(text.contains("\"id\""), "rows are cited from account_history, not from aggregates");
    }
}
