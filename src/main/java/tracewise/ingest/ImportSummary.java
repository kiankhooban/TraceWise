package tracewise.ingest;

import java.time.Instant;
import java.util.List;

/** earliest and latest are null when nothing was imported. */
public record ImportSummary(int rowsRead, int rowsImported, List<RejectedRow> rejected, int accounts,
        Instant earliest, Instant latest) {
}
