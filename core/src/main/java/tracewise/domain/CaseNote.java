package tracewise.domain;

import java.time.Instant;

/** An analyst's note on a case (F08). */
public record CaseNote(String text, String author, Instant createdAt) {
}
