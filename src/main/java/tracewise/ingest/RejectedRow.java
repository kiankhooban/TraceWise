package tracewise.ingest;

/** rowNumber is the line number in the file (the header is line 1). */
public record RejectedRow(int rowNumber, String reason) {
}
