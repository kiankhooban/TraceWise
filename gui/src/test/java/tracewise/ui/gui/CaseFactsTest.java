package tracewise.ui.gui;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import tracewise.domain.Alert;
import tracewise.domain.AlertStatus;
import tracewise.domain.PaymentChannel;
import tracewise.domain.Transaction;

/** The case fact header (docs/stage2/ui-design.md, after Hummingbird's case overview). */
class CaseFactsTest {

    static Transaction tx(String id, String day, String from, String to, String cad) {
        return new Transaction(id, Instant.parse(day + "T12:00:00Z"), from, to, new BigDecimal(cad), new BigDecimal(cad),
                "CAD", PaymentChannel.WIRE, "CA", false);
    }

    final Alert alert = new Alert("ALR-00002", "R4", "C1059", List.of("TX-1", "TX-2"), new BigDecimal("48000"), 65, 2,
            AlertStatus.IN_CASE, Instant.parse("2026-11-03T01:47:00Z"));

    @Test
    void theHeaderSummarisesTheFlaggedActivityOnly() {
        var facts = CaseFacts.of(alert, List.of(tx("TX-1", "2026-09-16", "EXT-2224", "C1059", "48000.00"),
                tx("TX-2", "2026-09-16", "C1059", "EXT-2137", "45500.00"),
                tx("TX-9", "2026-09-30", "C1059", "EXT-1", "10.00")));

        assertEquals("R4 ALR-00002", facts.trigger());
        assertEquals("C1059", facts.subject());
        assertEquals("09-16 to 09-16", facts.window());
        assertEquals("48,000.00 in  45,500.00 out", facts.net());
        assertEquals("HIGH 65", facts.risk());
    }

    @Test
    void aSideWithNoMoneyIsLeftOut() {
        var facts = CaseFacts.of(alert, List.of(tx("TX-1", "2026-09-16", "EXT-2224", "C1059", "48000.00")));
        assertEquals("48,000.00 in", facts.net());
    }

    @Test
    void withoutTheTransactionsTheWindowIsUnknown() {
        var facts = CaseFacts.of(alert, List.of());
        assertEquals("not loaded", facts.window());
        assertEquals("none", facts.net());
    }

    @Test
    void flaggedFlowsReadInOrOutFromTheSubjectsSide() {
        var flows = CaseFacts.flows(alert, List.of(tx("TX-2", "2026-09-16", "C1059", "EXT-2137", "45500.00"),
                tx("TX-1", "2026-09-15", "EXT-2224", "C1059", "48000.00"),
                tx("TX-9", "2026-09-30", "C1059", "EXT-1", "10.00")));
        assertEquals(List.of(new CaseFacts.Flow("In", "TX-1", "48,000.00", "EXT-2224", "09-15 12:00"),
                new CaseFacts.Flow("Out", "TX-2", "45,500.00", "EXT-2137", "09-16 12:00")), flows);
    }
}
