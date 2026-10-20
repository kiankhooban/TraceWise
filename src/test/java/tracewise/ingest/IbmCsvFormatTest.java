package tracewise.ingest;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tracewise.domain.PaymentChannel;

class IbmCsvFormatTest {

    static final List<String> HEADER = List.of("Timestamp", "From Bank", "Account", "To Bank", "Account",
            "Amount Received", "Receiving Currency", "Amount Paid", "Payment Currency", "Payment Format",
            "Is Laundering");

    private final IbmCsvFormat format = new IbmCsvFormat(
            new CurrencyConverter(Map.of("CAD", BigDecimal.ONE, "USD", new BigDecimal("1.5"))));

    private static final List<String> ROW = List.of("2022/09/01 00:20", "10", "8000EBD30", "10", "8000EBD30",
            "3697.34", "US Dollar", "3697.34", "US Dollar", "Reinvestment", "0");

    @Test
    void recognisesItsHeaderOnly() {
        assertTrue(format.matches(HEADER));
        assertFalse(format.matches(ScenarioCsvFormatTest.HEADER));
    }

    @Test
    void readsColumnsByPositionAndConvertsThePaidAmount() {
        var t = format.parse(ROW, 2);
        assertEquals(Instant.parse("2022-09-01T00:20:00Z"), t.timestamp());
        assertEquals("8000EBD30", t.senderAccountId());
        assertEquals("8000EBD30", t.receiverAccountId());
        assertEquals("USD", t.currency());
        assertEquals(new BigDecimal("5546.01"), t.amountCad());
        assertEquals(PaymentChannel.OTHER, t.channel());
        assertEquals(Boolean.FALSE, t.launderingLabel());
        assertFalse(t.isInternational());
    }

    @Test
    void derivesTheSameIdForTheSameRowAndPosition() {
        assertEquals(format.parse(ROW, 2).id(), format.parse(ROW, 2).id());
        assertNotEquals(format.parse(ROW, 2).id(), format.parse(ROW, 3).id());
        assertTrue(format.parse(ROW, 2).id().startsWith("IBM-"));
    }

    @Test
    void mapsPaymentFormatsToChannels() {
        assertEquals(PaymentChannel.CRYPTO, channelOf("Bitcoin"));
        assertEquals(PaymentChannel.CARD, channelOf("Credit Card"));
        assertEquals(PaymentChannel.CASH, channelOf("Cash"));
    }

    @Test
    void rejectsCurrenciesWithoutARate() {
        var row = new java.util.ArrayList<>(ROW);
        row.set(8, "Ruble");
        var e = assertThrows(IllegalArgumentException.class, () -> format.parse(row, 2));
        assertEquals("currency not in rate table: RUB", e.getMessage());
    }

    private PaymentChannel channelOf(String paymentFormat) {
        var row = new java.util.ArrayList<>(ROW);
        row.set(9, paymentFormat);
        return format.parse(row, 2).channel();
    }
}
