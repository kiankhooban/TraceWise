package tracewise.agent.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tracewise.agent.AgentFinding;
import tracewise.agent.AgentOutcome;
import tracewise.agent.AgentTrace;
import tracewise.agent.OutcomeStatus;
import tracewise.agent.Verdict;
import tracewise.cases.state.CaseStatus;
import tracewise.domain.Alert;
import tracewise.domain.AlertStatus;
import tracewise.domain.Case;
import tracewise.domain.PaymentChannel;
import tracewise.domain.Transaction;
import tracewise.llm.ModelConfig;
import tracewise.llm.ToolCall;
import tracewise.persistence.Database;
import tracewise.persistence.SqliteAlertRepository;
import tracewise.persistence.SqliteCaseRepository;
import tracewise.persistence.SqliteTransactionRepository;

/** The assistant's search tools (F10): filters are validated before they run. */
class AssistantToolsTest {

    @TempDir Path dir;
    Database db;
    final ToolRegistry registry = new ToolRegistry();

    @BeforeEach
    void setUp() {
        db = Database.open(dir.resolve("q.db"));
        var alerts = new SqliteAlertRepository(db);
        var cases = new SqliteCaseRepository(db);
        Instant t = Instant.parse("2026-09-01T00:00:00Z");
        new SqliteTransactionRepository(db).saveAll(List.of(tx("t1", "C1"), tx("t2", "C2"), tx("t3", "C3")));
        alerts.save(new Alert(null, "R3", "C1", List.of("t1"), new BigDecimal("60000.00"), 80, 1, AlertStatus.IN_CASE, t));
        alerts.save(new Alert(null, "R3", "C2", List.of("t2"), new BigDecimal("20000.00"), 50, 2, AlertStatus.IN_CASE, t));
        alerts.save(new Alert(null, "R5", "C3", List.of("t3"), new BigDecimal("90000.00"), 70, 1, AlertStatus.IN_CASE, t));
        cases.save(new Case("CAS-00001", t, List.of("ALR-00001"), CaseStatus.UNDER_INVESTIGATION));
        cases.save(new Case("CAS-00002", t, List.of("ALR-00002"), CaseStatus.UNDER_INVESTIGATION));
        var trace = new AgentTrace("ALR-00003", ModelConfig.of("anthropic", "claude-haiku-5-5", false), BigDecimal.ONE);
        trace.finish(new AgentOutcome("ALR-00003", OutcomeStatus.COMPLETED, "{}"));
        cases.save(new Case("CAS-00003", t, List.of("ALR-00003"), CaseStatus.ESCALATED, List.of(new AgentFinding(
                Verdict.ESCALATE, 0.8, List.of("Multiple clients have sent wire transfers to the same recipient."),
                List.of(), List.of(), "Nine senders paid C3 within four days.", false, trace))));
        alerts.save(new Alert(null, "R6", "C1", List.of("t1"), new BigDecimal("5000.00"), 30, 3, AlertStatus.NEW, t));
        registry.register(new QueryCasesTool(cases, alerts));
        registry.register(new QueryAlertsTool(alerts, cases));
        registry.register(new GetCaseTool(cases, alerts));
    }

    static Transaction tx(String id, String account) {
        return new Transaction(id, Instant.parse("2026-09-01T00:00:00Z"), "CASH", account, new BigDecimal("9000"),
                new BigDecimal("9000"), "CAD", PaymentChannel.CASH, "CA", null);
    }

    @AfterEach
    void close() {
        db.close();
    }

    ToolResult query(String args) {
        return registry.execute(new ToolCall("c1", QueryCasesTool.NAME, args));
    }

    JsonNode call(String tool, String args) throws Exception {
        var r = registry.execute(new ToolCall("c1", tool, args));
        assertFalse(r.error(), r.content());
        return new ObjectMapper().readTree(r.content());
    }

    List<String> caseIds(ToolResult r) throws Exception {
        JsonNode cases = new ObjectMapper().readTree(r.content()).get("cases");
        var ids = new ArrayList<String>();
        cases.forEach(c -> ids.add(c.get("case_id").asText()));
        return ids;
    }

    @Test
    void filtersByStatusRuleAndMinimumTotal() throws Exception {
        var result = query("{\"status\":\"UNDER_INVESTIGATION\",\"rule\":\"R3\",\"min_total_cad\":50000}");

        assertFalse(result.error(), result.content());
        assertEquals(List.of("CAS-00001"), caseIds(result));
        var row = new ObjectMapper().readTree(result.content()).get("cases").get(0);
        assertEquals("60000.00", row.get("total_cad").asText());
        assertEquals("C1", row.get("alerts").get(0).get("account_id").asText());
        assertEquals("t1", row.get("alerts").get(0).get("transaction_ids").get(0).asText(), "so totals can be cited");
    }

    @Test
    void noFilterListsEveryCase() throws Exception {
        assertEquals(List.of("CAS-00001", "CAS-00002", "CAS-00003"), caseIds(query("{}")));
        assertEquals(List.of("CAS-00003"), caseIds(query("{\"account_id\":\"C3\"}")));
    }

    @Test
    void openMeansNotYetDecided() throws Exception {
        assertEquals(List.of("CAS-00001", "CAS-00002"), caseIds(query("{\"status\":\"OPEN\"}")));
        assertEquals(List.of("CAS-00003"), caseIds(query("{\"status\":\"ESCALATED\"}")));
    }

    @Test
    void anInvalidFilterIsRejectedWithWhatIsAllowed() {
        var badStatus = query("{\"status\":\"OPENED\"}");
        assertTrue(badStatus.error());
        assertTrue(badStatus.content().contains("UNDER_INVESTIGATION"), badStatus.content());

        assertTrue(query("{\"rule\":\"R9\"}").error());
        assertTrue(query("{\"min_total_cad\":-5}").error());
        assertTrue(query("{\"amount\":5}").error(), "unknown field");
    }

    @Test
    void queryAlertsFindsAlertsOutsideCasesToo() throws Exception {
        var rows = call(QueryAlertsTool.NAME, "{\"status\":\"NEW\"}").get("alerts");

        assertEquals(1, rows.size());
        assertEquals("ALR-00004", rows.get(0).get("alert_id").asText());
        assertEquals("t1", rows.get(0).get("transaction_ids").get(0).asText());
        assertTrue(rows.get(0).get("case_id").isNull());
        assertEquals("CAS-00001", call(QueryAlertsTool.NAME, "{\"rule\":\"R3\",\"account_id\":\"C1\"}")
                .get("alerts").get(0).get("case_id").asText());
        assertTrue(registry.execute(new ToolCall("c1", QueryAlertsTool.NAME, "{\"status\":\"OPEN\"}")).error());
    }

    @Test
    void getCaseShowsTheLatestFindingAndWhy() throws Exception {
        var c = call(GetCaseTool.NAME, "{\"case_id\":\"CAS-00003\"}");

        assertEquals("ESCALATED", c.get("status").asText());
        assertEquals("ESCALATE", c.get("latest_finding").get("verdict").asText());
        assertEquals("Nine senders paid C3 within four days.", c.get("latest_finding").get("narrative").asText());
        assertEquals("ALR-00003", c.get("alerts").get(0).get("alert_id").asText());
        assertEquals("t3", c.get("alerts").get(0).get("transaction_ids").get(0).asText());
        assertTrue(call(GetCaseTool.NAME, "{\"case_id\":\"CAS-00001\"}").get("latest_finding").isNull());
        assertTrue(registry.execute(new ToolCall("c1", GetCaseTool.NAME, "{\"case_id\":\"CAS-09999\"}")).error());
    }
}
