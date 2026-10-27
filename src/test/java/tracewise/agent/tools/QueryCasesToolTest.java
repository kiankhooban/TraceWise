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
import tracewise.cases.state.CaseStatus;
import tracewise.domain.Alert;
import tracewise.domain.AlertStatus;
import tracewise.domain.Case;
import tracewise.domain.PaymentChannel;
import tracewise.domain.Transaction;
import tracewise.llm.ToolCall;
import tracewise.persistence.Database;
import tracewise.persistence.SqliteAlertRepository;
import tracewise.persistence.SqliteCaseRepository;
import tracewise.persistence.SqliteTransactionRepository;

/** query_cases: the assistant's structured search, validated before it runs (F10). */
class QueryCasesToolTest {

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
        cases.save(new Case("CAS-00003", t, List.of("ALR-00003"), CaseStatus.ESCALATED));
        registry.register(new QueryCasesTool(cases, alerts));
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
    }

    @Test
    void noFilterListsEveryCase() throws Exception {
        assertEquals(List.of("CAS-00001", "CAS-00002", "CAS-00003"), caseIds(query("{}")));
        assertEquals(List.of("CAS-00003"), caseIds(query("{\"account_id\":\"C3\"}")));
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
}
