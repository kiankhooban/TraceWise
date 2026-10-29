package tracewise.ingest;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/** Converts amounts to Canadian dollars with a fixed rate table. */
public class CurrencyConverter {

    private final Map<String, BigDecimal> ratesToCad;

    public CurrencyConverter(Map<String, BigDecimal> ratesToCad) {
        this.ratesToCad = Map.copyOf(ratesToCad);
    }

    public static CurrencyConverter bankOfCanadaDefaults() {
        var props = new Properties();
        try (InputStream in = CurrencyConverter.class.getResourceAsStream("/tracewise/rates-to-cad.properties")) {
            props.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        var rates = new HashMap<String, BigDecimal>();
        props.forEach((code, rate) -> rates.put((String) code, new BigDecimal((String) rate)));
        return new CurrencyConverter(rates);
    }

    public BigDecimal toCad(BigDecimal amount, String currency) {
        BigDecimal rate = ratesToCad.get(currency);
        if (rate == null) {
            throw new IllegalArgumentException("currency not in rate table: " + currency);
        }
        return amount.multiply(rate).setScale(2, RoundingMode.HALF_EVEN);
    }
}
