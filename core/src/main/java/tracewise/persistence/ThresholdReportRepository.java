package tracewise.persistence;

import java.util.List;
import tracewise.domain.ThresholdReport;

public interface ThresholdReportRepository {

    /** Stores the report; returns false if the same report is already in the register. */
    boolean save(ThresholdReport report);

    List<ThresholdReport> findAll();
}
