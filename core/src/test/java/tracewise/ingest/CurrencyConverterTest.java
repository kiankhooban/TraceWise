package tracewise.ingest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CurrencyConverterTest {

    private final CurrencyConverter converter =
            new CurrencyConverter(Map.of("CAD", BigDecimal.ONE, "USD", new BigDecimal("1.4243")));

    @Test
    void convertsToCadRoundedToCents() {
        assertEquals(new BigDecimal("14243.00"), converter.toCad(new BigDecimal("10000"), "USD"));
        assertEquals(new BigDecimal("1.42"), converter.toCad(new BigDecimal("1"), "USD"));
    }

    @Test
    void cadIsUnchanged() {
        assertEquals(new BigDecimal("9999.99"), converter.toCad(new BigDecimal("9999.99"), "CAD"));
    }

    @Test
    void unknownCurrencyIsRejectedByName() {
        var e = assertThrows(IllegalArgumentException.class, () -> converter.toCad(BigDecimal.TEN, "RUB"));
        assertEquals("currency not in rate table: RUB", e.getMessage());
    }

    @Test
    void defaultTableHoldsBankOfCanadaRates() {
        var defaults = CurrencyConverter.bankOfCanadaDefaults();
        assertEquals(new BigDecimal("14243.00"), defaults.toCad(new BigDecimal("10000"), "USD"));
        assertThrows(IllegalArgumentException.class, () -> defaults.toCad(BigDecimal.ONE, "BTC"));
    }
}
