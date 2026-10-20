package tracewise.agent;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tracewise.domain.Alert;
import tracewise.domain.AlertStatus;

class InvestigationTaskTest {

    static final Alert ALERT = new Alert("ALR-00001", "R3", "S-C1", List.of("t1", "t2", "t3"), new BigDecimal("28500"),
            82, 1, AlertStatus.NEW, Instant.parse("2026-10-20T12:00:00Z"));
    final InvestigationTask task = new InvestigationTask(ALERT);

    static final String GOOD = """
            The deposits are structured.
            ```json
            {
              "verdict": "ESCALATE",
              "confidence": 0.85,
              "indicators": ["Cash deposits just under the reporting threshold"],
              "cited_transactions": [
                {"transaction_id": "t1", "account_id": "S-C1", "amount_cad": 9500.00, "date": "2026-09-01"}
              ],
              "totals": [{"description": "Deposits in 3 days", "amount_cad": 28500, "transaction_ids": ["t1", "t2", "t3"]}],
              "narrative": "Three deposits of about $9,500 in three days."
            }
            ```
            """;

    @Test
    void opensWithTheAlertDetails() {
        String m = task.initialMessage();
        for (String expected : List.of("ALR-00001", "R3", "S-C1", "t1, t2, t3", "28500")) {
            assertTrue(m.contains(expected), expected + " missing from: " + m);
        }
        assertEquals(Set.of("account_history", "counterparties"), task.toolNames());
        assertTrue(task.systemPrompt().contains("\"verdict\""));
    }

    @Test
    void parsesTheStructuredFinding() {
        assertNull(task.outputProblem(GOOD));
        var f = task.parseFinding(GOOD, false);
        assertEquals(Verdict.ESCALATE, f.verdict());
        assertEquals(0.85, f.confidence());
        assertEquals(List.of("Cash deposits just under the reporting threshold"), f.indicators());
        assertEquals(List.of(new Claim("t1", "S-C1", new BigDecimal("9500.00"), LocalDate.parse("2026-09-01"))), f.claims());
        assertEquals(List.of("t1", "t2", "t3"), f.totals().get(0).transactionIds());
        assertEquals("Three deposits of about $9,500 in three days.", f.narrative());
        assertFalse(f.incomplete());
        assertNull(f.verification());
    }

    @Test
    void acceptsABareJsonAnswer() {
        assertNull(task.outputProblem("{\"verdict\":\"DISMISS\",\"confidence\":0.6,\"indicators\":[],"
                + "\"cited_transactions\":[],\"totals\":[],\"narrative\":\"Payroll pattern.\"}"));
    }

    @Test
    void namesWhatIsWrongWithABadAnswer() {
        assertTrue(task.outputProblem("I would escalate this.").contains("JSON"));
        assertTrue(task.outputProblem(GOOD.replace("\"ESCALATE\"", "\"MAYBE\"")).contains("verdict"));
        assertTrue(task.outputProblem(GOOD.replace("0.85", "85")).contains("confidence"));
        assertTrue(task.outputProblem(GOOD.replace("2026-09-01", "Sept 1")).contains("date"));
        assertTrue(task.outputProblem(GOOD.replace("\"narrative\"", "\"story\"")).contains("narrative"));
    }
}
