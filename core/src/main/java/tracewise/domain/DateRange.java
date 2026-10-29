package tracewise.domain;

import java.time.Instant;

/** Half-open time range [from, to). */
public record DateRange(Instant from, Instant to) {

    public static final DateRange ALL = new DateRange(Instant.EPOCH, Instant.parse("9999-12-31T00:00:00Z"));

    public boolean contains(Instant t) {
        return !t.isBefore(from) && t.isBefore(to);
    }

    /** Whole UTC days, the last one included; a null end is unbounded. */
    public static DateRange ofDays(java.time.LocalDate from, java.time.LocalDate to) {
        return new DateRange(from == null ? ALL.from() : from.atStartOfDay().toInstant(java.time.ZoneOffset.UTC),
                to == null ? ALL.to() : to.plusDays(1).atStartOfDay().toInstant(java.time.ZoneOffset.UTC));
    }
}
