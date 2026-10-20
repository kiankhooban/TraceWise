package tracewise.ingest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.List;
import tracewise.domain.PaymentChannel;
import tracewise.domain.Transaction;

/** TraceWise's own scenario layout: explicit ids, ISO timestamps (UTC) and the counterparty country. */
public class ScenarioCsvFormat implements TransactionFormat {

    static final List<String> HEADER = List.of("transaction_id", "timestamp", "sender_bank", "sender_account",
            "receiver_bank", "receiver_account", "amount", "currency", "channel", "counterparty_country",
            "is_laundering");

    private final CurrencyConverter converter;

    public ScenarioCsvFormat(CurrencyConverter converter) {
        this.converter = converter;
    }

    @Override
    public String name() {
        return "TraceWise scenario CSV";
    }

    @Override
    public List<String> header() {
        return HEADER;
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
        String id = required(row, 0);
        String timestamp = required(row, 1);
        BigDecimal amount;
        try {
            amount = new BigDecimal(required(row, 6));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("invalid amount: " + row.get(6));
        }
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        String currency = required(row, 7);
        PaymentChannel channel;
        try {
            channel = PaymentChannel.valueOf(required(row, 8));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unknown channel: " + row.get(8));
        }
        String label = row.get(10);
        return new Transaction(id, parseTimestamp(timestamp), required(row, 3), required(row, 5),
                converter.toCad(amount, currency), amount, currency, channel, row.get(9),
                label.isEmpty() ? null : "1".equals(label));
    }

    private static String required(List<String> row, int column) {
        String value = row.get(column).trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("missing field: " + HEADER.get(column));
        }
        return value;
    }

    private static Instant parseTimestamp(String value) {
        try {
            return LocalDateTime.parse(value).toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("invalid timestamp: " + value);
        }
    }
}
