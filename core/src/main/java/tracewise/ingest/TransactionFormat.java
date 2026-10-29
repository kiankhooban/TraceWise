package tracewise.ingest;

import java.util.List;
import tracewise.domain.Transaction;

/** Strategy: one supported CSV layout. ImportService picks the first format whose header matches. */
public interface TransactionFormat {

    /** Shown to the analyst in the import preview. */
    String name();

    boolean matches(List<String> header);

    List<String> header();

    /** Parses one data row; throws IllegalArgumentException with the rejection reason if invalid. */
    Transaction parse(List<String> row, int rowNumber);
}
