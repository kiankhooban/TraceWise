package tracewise.ingest;

import java.util.List;

/** rowNumber is the line number in the file (the header is line 1). */
public record RejectedRow(int rowNumber, String reason) {

    /** Reasons whose value differs on every row; they are counted without it. */
    private static final List<String> PER_ROW = List.of("duplicate transaction id", "invalid amount", "invalid timestamp");

    /** The reason without a value that differs per row, for counting: "currency not in rate table: BTC" stays whole. */
    public String kind() {
        String prefix = reason.split(": ", 2)[0];
        return PER_ROW.contains(prefix) ? prefix : reason;
    }
}
