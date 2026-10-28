package tracewise.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class DateRangeTest {

    @Test
    void daysAreUtcAndTheLastDayIsIncludedAndNullMeansNoLimit() {
        var r = DateRange.ofDays(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 2));
        assertEquals(Instant.parse("2026-09-01T00:00:00Z"), r.from());
        assertEquals(Instant.parse("2026-09-03T00:00:00Z"), r.to());
        assertEquals(DateRange.ALL, DateRange.ofDays(null, null));
    }
}
