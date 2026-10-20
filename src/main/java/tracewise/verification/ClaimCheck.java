package tracewise.verification;

/** The result for one claim; claim is the claim in words, reason says why it was rejected (empty if confirmed). */
public record ClaimCheck(String claim, boolean confirmed, String reason) {
}
