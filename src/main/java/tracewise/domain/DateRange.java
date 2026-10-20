package tracewise.domain;

import java.time.Instant;

/** Half-open time range [from, to). */
public record DateRange(Instant from, Instant to) {

    public static final DateRange ALL = new DateRange(Instant.EPOCH, Instant.parse("9999-12-31T00:00:00Z"));

    public boolean contains(Instant t) {
        return !t.isBefore(from) && t.isBefore(to);
    }
}
