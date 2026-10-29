package tracewise.ingest;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import tracewise.domain.PaymentChannel;
import tracewise.domain.Transaction;

/**
 * SAML-D layout (Oztas et al., IEEE ICEBE 2023; CC BY-NC-SA 4.0): a UK bank's transactions with 11 normal and 17
 * suspicious typologies. The bank is in the UK, so UK counterparties are domestic. Cash rows name two accounts; the
 * cash counter replaces the one that handed over or took the cash, as in the scenario format. Amounts are in pounds
 * whatever the payment currency, so no row is rejected for its currency. The typology column is never kept: it names
 * the answer.
 */
public class SamlDCsvFormat implements TransactionFormat {

    static final List<String> HEADER = List.of("Time", "Date", "Sender_account", "Receiver_account", "Amount",
            "Payment_currency", "Received_currency", "Sender_bank_location", "Receiver_bank_location", "Payment_type",
            "Is_laundering", "Laundering_type");

    private static final Map<String, String> COUNTRY_CODES = Map.ofEntries(
            Map.entry("Pakistan", "PK"), Map.entry("Austria", "AT"), Map.entry("Albania", "AL"), Map.entry("Japan", "JP"),
            Map.entry("Morocco", "MA"), Map.entry("Germany", "DE"), Map.entry("France", "FR"), Map.entry("Mexico", "MX"),
            Map.entry("Nigeria", "NG"), Map.entry("Netherlands", "NL"), Map.entry("Spain", "ES"), Map.entry("India", "IN"),
            Map.entry("USA", "US"), Map.entry("Italy", "IT"), Map.entry("Switzerland", "CH"), Map.entry("UAE", "AE"),
            Map.entry("Turkey", "TR"));

    private static final Map<String, PaymentChannel> CHANNELS = Map.of(
            "Credit card", PaymentChannel.CARD, "Debit card", PaymentChannel.CARD, "Cheque", PaymentChannel.CHEQUE,
            "ACH", PaymentChannel.ACH, "Cross-border", PaymentChannel.WIRE, "Cash Deposit", PaymentChannel.CASH,
            "Cash Withdrawal", PaymentChannel.CASH);

    private static final String HOME = "UK";

    /** Amount is in pounds whatever Payment_currency says: its mean is about 10,000 for every currency. */
    private static final String AMOUNT_CURRENCY = "GBP";

    private final CurrencyConverter converter;

    public SamlDCsvFormat(CurrencyConverter converter) {
        this.converter = converter;
    }

    @Override
    public String name() {
        return "SAML-D synthetic AML CSV";
    }

    @Override
    public boolean matches(List<String> header) {
        return HEADER.equals(header);
    }

    @Override
    public List<String> header() {
        return HEADER;
    }

    @Override
    public Transaction parse(List<String> row, int rowNumber) {
        if (row.size() != HEADER.size()) {
            throw new IllegalArgumentException("expected " + HEADER.size() + " columns, found " + row.size());
        }
        LocalDateTime when;
        try {
            when = LocalDateTime.parse(row.get(1) + "T" + row.get(0));
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("invalid timestamp: " + row.get(1) + " " + row.get(0));
        }
        BigDecimal amount;
        try {
            amount = new BigDecimal(row.get(4));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("invalid amount: " + row.get(4));
        }
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        PaymentChannel channel = CHANNELS.get(row.get(9));
        if (channel == null) {
            throw new IllegalArgumentException("unknown payment type: " + row.get(9));
        }
        if (row.get(2).isBlank() || row.get(3).isBlank()) {
            throw new IllegalArgumentException("missing field: account");
        }
        String sender = row.get(9).equals("Cash Deposit") ? Transaction.CASH : row.get(2);
        String receiver = row.get(9).equals("Cash Withdrawal") ? Transaction.CASH : row.get(3);
        return new Transaction(derivedId(row, rowNumber), when.toInstant(ZoneOffset.UTC), sender, receiver,
                converter.toCad(amount, AMOUNT_CURRENCY), amount, AMOUNT_CURRENCY, channel, foreignCountry(row),
                "1".equals(row.get(10)));
    }

    /** The other side's country when either bank is abroad; null for a domestic (UK to UK) transaction. */
    private static String foreignCountry(List<String> row) {
        String abroad = !HOME.equals(row.get(7)) ? row.get(7) : !HOME.equals(row.get(8)) ? row.get(8) : null;
        if (abroad == null) {
            return null;
        }
        String code = COUNTRY_CODES.get(abroad);
        if (code == null) {
            throw new IllegalArgumentException("unknown bank location: " + abroad);
        }
        return code;
    }

    // Re-importing the same file must give the same ids, so the id depends only on content and position.
    private static String derivedId(List<String> row, int rowNumber) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest((rowNumber + ":" + String.join(",", row)).getBytes(StandardCharsets.UTF_8));
            return "SML-" + HexFormat.of().formatHex(hash, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
