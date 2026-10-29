package tracewise.ingest;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tracewise.domain.PaymentChannel;

class ScenarioCsvFormatTest {

    static final List<String> HEADER = List.of("transaction_id", "timestamp", "sender_bank", "sender_account",
            "receiver_bank", "receiver_account", "amount", "currency", "channel", "counterparty_country",
            "is_laundering");

    private final ScenarioCsvFormat format = new ScenarioCsvFormat(
            new CurrencyConverter(Map.of("CAD", BigDecimal.ONE, "USD", new BigDecimal("1.5"))));

    private static List<String> row(String... fields) {
        return List.of(fields);
    }

    @Test
    void recognisesItsHeaderOnly() {
        assertTrue(format.matches(HEADER));
        assertFalse(format.matches(List.of("Timestamp", "From Bank", "Account")));
    }

    @Test
    void parsesACashDeposit() {
        var t = format.parse(row("S1-001", "2026-09-01T10:15:00", "", "CASH", "001", "C100",
                "9500.00", "CAD", "CASH", "CA", "1"), 2);
        assertEquals("S1-001", t.id());
        assertEquals(Instant.parse("2026-09-01T10:15:00Z"), t.timestamp());
        assertEquals("CASH", t.senderAccountId());
        assertEquals("C100", t.receiverAccountId());
        assertEquals(new BigDecimal("9500.00"), t.amountCad());
        assertEquals(PaymentChannel.CASH, t.channel());
        assertTrue(t.isCashDeposit());
        assertFalse(t.isInternational());
        assertEquals(Boolean.TRUE, t.launderingLabel());
    }

    @Test
    void convertsForeignAmountsAndFlagsInternationalTransfers() {
        var t = format.parse(row("S2-001", "2026-09-02T09:00:00", "001", "C200", "", "X9", "8000",
                "USD", "WIRE", "AE", ""), 3);
        assertEquals(new BigDecimal("12000.00"), t.amountCad());
        assertEquals(new BigDecimal("8000"), t.originalAmount());
        assertEquals("USD", t.currency());
        assertTrue(t.isInternational());
        assertNull(t.launderingLabel());
    }

    @Test
    void rejectsInvalidRowsWithAReason() {
        assertReason("amount must be positive", row("X", "2026-09-01T10:00:00", "", "A", "", "B", "-5", "CAD", "WIRE", "CA", ""));
        assertReason("invalid timestamp: 01/09/2026", row("X", "01/09/2026", "", "A", "", "B", "5", "CAD", "WIRE", "CA", ""));
        assertReason("missing field: transaction_id", row("", "2026-09-01T10:00:00", "", "A", "", "B", "5", "CAD", "WIRE", "CA", ""));
        assertReason("currency not in rate table: RUB", row("X", "2026-09-01T10:00:00", "", "A", "", "B", "5", "RUB", "WIRE", "CA", ""));
        assertReason("expected 11 columns, found 2", row("X", "Y"));
    }

    private void assertReason(String reason, List<String> fields) {
        var e = assertThrows(IllegalArgumentException.class, () -> format.parse(fields, 7));
        assertEquals(reason, e.getMessage());
    }
}
