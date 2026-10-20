package tracewise.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import tracewise.domain.Transaction;

/**
 * Seeded, reproducible scenario data in the TraceWise scenario CSV format (Stage 1 report, 1.1.8):
 * one planted scheme per rule, boundary cases that must not fire, and normal customers that must
 * trigger nothing. Ids start with the scheme name; is_laundering marks the planted laundering.
 * Usage: java -cp target/tracewise.jar tracewise.tools.ScenarioGenerator SEED OUT.csv
 */
public final class ScenarioGenerator {

    static final String HEADER = "transaction_id,timestamp,sender_bank,sender_account,receiver_bank,"
            + "receiver_account,amount,currency,channel,counterparty_country,is_laundering";
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final LocalDateTime START = LocalDateTime.parse("2026-09-01T00:00:00");

    private final Random random;
    private final List<String[]> rows = new ArrayList<>();
    private int sequence;

    private ScenarioGenerator(long seed) {
        this.random = new Random(seed);
    }

    public static List<String> generate(long seed) {
        var g = new ScenarioGenerator(seed);
        g.plantedSchemes();
        g.boundaryCases();
        for (int c = 1; c <= 60; c++) {
            g.normalCustomer(String.format("N-C%02d", c));
        }
        var lines = new ArrayList<String>();
        lines.add(HEADER);
        g.rows.stream().sorted(Comparator.comparing((String[] r) -> r[1]).thenComparing(r -> r[0]))
                .forEach(r -> lines.add(String.join(",", r)));
        return lines;
    }

    public static void main(String[] args) throws IOException {
        Files.write(Path.of(args[1]), generate(Long.parseLong(args[0])));
    }

    private void plantedSchemes() {
        // R3 structuring: four cash deposits in the $8,000-$9,999.99 band, 30 hours apart (no 24h pair, so no R1)
        for (int i = 0; i < 4; i++) {
            cash("STRUCT", "S-C1", true, at(2, i * 30), amount(8_600, 9_950), true);
        }
        // R1 large cash: one $15,000 deposit, and two deposits totalling exactly $10,000 within 24 hours
        cash("LCASH", "L-C1", true, at(5, 10), "15000.00", false);
        cash("LCASH", "L-C2", true, at(9, 9), "6000.00", false);
        cash("LCASH", "L-C2", true, at(9, 29), "4000.00", false);
        // R2 international: one incoming USD wire, and four outgoing wires to the UAE totalling $10,400 in a day
        wire("INTL", "EXT-US-1", "I-C1", at(11, 14), "8500.00", "USD", "US", false);
        for (int i = 0; i < 4; i++) {
            wire("INTL", "I-C2", "EXT-AE-" + (i + 1), at(14, 9 + i * 5), "2600.00", "CAD", "AE", true);
        }
        // R4 pass-through: $48,000 in, about 95% out five hours later
        wire("PASS", "EXT-P-SRC", "P-C1", at(16, 10), "48000.00", "CAD", "CA", true);
        wire("PASS", "P-C1", "EXT-P-DST", at(16, 15), "45500.00", "CAD", "CA", true);
        // R5 fan-in: six different senders to one account within five days
        for (int i = 1; i <= 6; i++) {
            wire("FANIN", "EXT-FI-" + i, "F-C1", at(18 + i / 2, 8 + i), amount(1_500, 3_000), "CAD", "CA", true);
        }
        // R6 fan-out: one account pays seven different recipients within three days
        for (int i = 1; i <= 7; i++) {
            wire("FANOUT", "O-C1", "EXT-FO-" + i, at(22 + i / 3, 7 + i), amount(1_200, 2_800), "CAD", "CA", true);
        }
    }

    private void boundaryCases() {
        // just below the R3 band: three $7,999.99 deposits within a week
        for (int i = 0; i < 3; i++) {
            cash("EDGE", "E-C1", true, at(3, i * 30), "7999.99", false);
        }
        // in the R3 band but spread so no 7-day window holds three deposits
        cash("EDGE", "E-C2", true, at(4, 0), "9000.00", false);
        cash("EDGE", "E-C2", true, at(8, 0), "9000.00", false);
        cash("EDGE", "E-C2", true, at(12, 12), "9000.00", false);
        // one cent under the R1 threshold
        cash("EDGE", "E-C3", true, at(6, 12), "9999.99", false);
        // four distinct senders in a week: one short of R5
        for (int i = 1; i <= 4; i++) {
            wire("EDGE", "EXT-E4-" + i, "E-C4", at(20, i * 3), "900.00", "CAD", "CA", false);
        }
    }

    private void normalCustomer(String c) {
        for (int day : new int[] {1, 15}) {
            wire("N", "EXT-" + c + "-EMP", c, at(day, 9), amount(1_800, 4_500), "CAD", "CA", false);
        }
        wire("N", c, "EXT-" + c + "-RENT", at(1, 12), amount(1_200, 2_200), "CAD", "CA", false);
        for (int week = 0; week < 4; week++) {
            for (int p = 0; p < 2; p++) {
                String shop = "EXT-" + c + "-SHOP" + (1 + random.nextInt(3));
                add("N", c, shop, at(2 + week * 7 + p * 3, 10 + random.nextInt(9)), amount(15, 300), "CAD",
                        "CARD", "CA", false);
            }
        }
        if (random.nextBoolean()) {
            cash("N", c, true, at(5 + random.nextInt(20), 11), amount(200, 2_500), false);
        }
    }

    private void cash(String scheme, String account, boolean deposit, LocalDateTime when, String amount,
            boolean laundering) {
        if (deposit) {
            add(scheme, "CASH", account, when, amount, "CAD", "CASH", "CA", laundering);
        } else {
            add(scheme, account, "CASH", when, amount, "CAD", "CASH", "CA", laundering);
        }
    }

    private void wire(String scheme, String from, String to, LocalDateTime when, String amount, String currency,
            String country, boolean laundering) {
        add(scheme, from, to, when, amount, currency, "WIRE", country, laundering);
    }

    private void add(String scheme, String from, String to, LocalDateTime when, String amount, String currency,
            String channel, String country, boolean laundering) {
        rows.add(new String[] {String.format("%s-%04d", scheme, ++sequence), TIMESTAMP.format(when),
                bank(from), from, bank(to), to, amount, currency, channel, country, laundering ? "1" : "0"});
    }

    private static String bank(String account) {
        return Transaction.CASH.equals(account) ? "" : account.startsWith(Transaction.EXTERNAL_PREFIX) ? "999" : "001";
    }

    private LocalDateTime at(int day, int hours) {
        return START.plusDays(day - 1L).plusHours(hours).plusMinutes(random.nextInt(50));
    }

    private String amount(int min, int max) {
        return String.format(Locale.ROOT, "%.2f", min + random.nextDouble() * (max - min));
    }
}
