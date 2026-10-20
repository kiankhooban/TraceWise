package tracewise.ingest;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import tracewise.domain.PaymentChannel;
import tracewise.domain.Transaction;

/**
 * IBM synthetic AML layout (Altman et al., 2023). It has no id column and two columns named
 * "Account", so columns are read by position, and ids are derived from row content and position.
 */
public class IbmCsvFormat implements TransactionFormat {

    static final List<String> HEADER = List.of("Timestamp", "From Bank", "Account", "To Bank", "Account",
            "Amount Received", "Receiving Currency", "Amount Paid", "Payment Currency", "Payment Format",
            "Is Laundering");

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");

    private static final Map<String, String> CURRENCY_CODES = Map.ofEntries(
            Map.entry("US Dollar", "USD"), Map.entry("Euro", "EUR"), Map.entry("Yuan", "CNY"),
            Map.entry("Yen", "JPY"), Map.entry("UK Pound", "GBP"), Map.entry("Rupee", "INR"),
            Map.entry("Canadian Dollar", "CAD"), Map.entry("Australian Dollar", "AUD"),
            Map.entry("Mexican Peso", "MXN"), Map.entry("Swiss Franc", "CHF"), Map.entry("Brazil Real", "BRL"),
            Map.entry("Ruble", "RUB"), Map.entry("Saudi Riyal", "SAR"), Map.entry("Shekel", "ILS"),
            Map.entry("Bitcoin", "BTC"));

    private static final Map<String, PaymentChannel> CHANNELS = Map.of(
            "Cash", PaymentChannel.CASH, "Wire", PaymentChannel.WIRE, "ACH", PaymentChannel.ACH,
            "Cheque", PaymentChannel.CHEQUE, "Credit Card", PaymentChannel.CARD,
            "Bitcoin", PaymentChannel.CRYPTO, "Reinvestment", PaymentChannel.OTHER);

    private final CurrencyConverter converter;

    public IbmCsvFormat(CurrencyConverter converter) {
        this.converter = converter;
    }

    @Override
    public boolean matches(List<String> header) {
        return HEADER.equals(header);
    }

    @Override
    public Transaction parse(List<String> row, int rowNumber) {
        if (row.size() != HEADER.size()) {
            throw new IllegalArgumentException("expected " + HEADER.size() + " columns, found " + row.size());
        }
        Instant timestamp;
        try {
            timestamp = LocalDateTime.parse(row.get(0), TIMESTAMP).toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("invalid timestamp: " + row.get(0));
        }
        BigDecimal amount;
        try {
            amount = new BigDecimal(row.get(7));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("invalid amount: " + row.get(7));
        }
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        String currency = CURRENCY_CODES.get(row.get(8));
        if (currency == null) {
            throw new IllegalArgumentException("unknown currency: " + row.get(8));
        }
        PaymentChannel channel = CHANNELS.get(row.get(9));
        if (channel == null) {
            throw new IllegalArgumentException("unknown payment format: " + row.get(9));
        }
        if (row.get(2).isBlank() || row.get(4).isBlank()) {
            throw new IllegalArgumentException("missing field: Account");
        }
        return new Transaction(derivedId(row, rowNumber), timestamp, row.get(2), row.get(4),
                converter.toCad(amount, currency), amount, currency, channel, null, "1".equals(row.get(10)));
    }

    // Re-importing the same file must give the same ids, so the id depends only on content and position.
    private static String derivedId(List<String> row, int rowNumber) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest((rowNumber + ":" + String.join(",", row)).getBytes(StandardCharsets.UTF_8));
            return "IBM-" + HexFormat.of().formatHex(hash, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
