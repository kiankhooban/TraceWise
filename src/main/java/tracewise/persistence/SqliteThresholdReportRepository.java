package tracewise.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import tracewise.domain.ThresholdReport;

public class SqliteThresholdReportRepository implements ThresholdReportRepository {

    private final Database db;

    public SqliteThresholdReportRepository(Database db) {
        this.db = db;
    }

    @Override
    public boolean save(ThresholdReport r) {
        boolean[] inserted = {false};
        db.inTransaction(() -> {
            inserted[0] = Jdbc.update(db, "INSERT OR IGNORE INTO threshold_reports (id, rule_id, account_id, "
                    + "total_cad, window_start, window_end) VALUES (?, ?, ?, ?, ?, ?)", r.id(), r.ruleId(),
                    r.accountId(), r.totalCad().toPlainString(), r.windowStart().toEpochMilli(),
                    r.windowEnd().toEpochMilli()) == 1;
            if (inserted[0]) {
                for (String id : r.transactionIds()) {
                    Jdbc.update(db, "INSERT INTO threshold_report_transactions (report_id, transaction_id) "
                            + "VALUES (?, ?)", r.id(), id);
                }
            }
        });
        return inserted[0];
    }

    @Override
    public List<ThresholdReport> findAll() {
        return Jdbc.query(db, "SELECT r.id, r.rule_id, r.account_id, (SELECT group_concat(transaction_id, ',') "
                + "FROM threshold_report_transactions t WHERE t.report_id = r.id), r.total_cad, r.window_start, "
                + "r.window_end FROM threshold_reports r ORDER BY r.window_start, r.id",
                rs -> new ThresholdReport(rs.getString(1), rs.getString(2), rs.getString(3),
                        Arrays.stream(rs.getString(4).split(",")).sorted().toList(), new BigDecimal(rs.getString(5)),
                        Instant.ofEpochMilli(rs.getLong(6)), Instant.ofEpochMilli(rs.getLong(7))));
    }
}
