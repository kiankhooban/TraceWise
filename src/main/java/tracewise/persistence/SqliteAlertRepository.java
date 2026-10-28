package tracewise.persistence;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import tracewise.domain.Alert;
import tracewise.domain.AlertStatus;
import tracewise.metrics.AlertLabel;
import tracewise.monitoring.AlertFilter;
import tracewise.monitoring.RuleMatch;

public class SqliteAlertRepository implements AlertRepository {

    private static final String SELECT = "SELECT a.seq, a.rule_id, a.account_id, "
            + "(SELECT group_concat(transaction_id, ',') FROM alert_transactions t WHERE t.alert_seq = a.seq), "
            + "a.total_cad, a.risk_score, a.priority, a.status, a.created_at FROM alerts a ";

    private final Database db;

    public SqliteAlertRepository(Database db) {
        this.db = db;
    }

    @Override
    public void save(Alert alert) {
        db.inTransaction(() -> {
            Jdbc.update(db, "INSERT INTO alerts (rule_id, account_id, total_cad, risk_score, priority, status, "
                    + "created_at) VALUES (?, ?, ?, ?, ?, ?, ?)", alert.ruleId(), alert.accountId(),
                    alert.totalCad().toPlainString(), alert.riskScore(), alert.priority(), alert.status().name(),
                    alert.createdAt().toEpochMilli());
            long seq = Jdbc.query(db, "SELECT last_insert_rowid()", rs -> rs.getLong(1)).get(0);
            for (String id : alert.transactionIds()) {
                Jdbc.update(db, "INSERT INTO alert_transactions (alert_seq, transaction_id) VALUES (?, ?)", seq, id);
            }
        });
    }

    @Override
    public List<Alert> find(AlertFilter f) {
        return Jdbc.query(db, SELECT + "WHERE (?1 IS NULL OR a.rule_id = ?1) AND (?2 IS NULL OR a.status = ?2) "
                + "AND (?3 IS NULL OR CAST(a.total_cad AS REAL) >= ?3) ORDER BY a.risk_score DESC, a.seq",
                SqliteAlertRepository::map, f.ruleId(), f.status() == null ? null : f.status().name(),
                f.minTotalCad() == null ? null : f.minTotalCad().doubleValue());
    }

    @Override
    public java.util.Set<String> accountsWithAlerts(java.util.Collection<String> accountIds) {
        if (accountIds.isEmpty()) {
            return java.util.Set.of();
        }
        String placeholders = String.join(",", accountIds.stream().map(a -> "?").toList());
        return new java.util.HashSet<>(Jdbc.query(db, "SELECT DISTINCT account_id FROM alerts WHERE account_id IN ("
                + placeholders + ")", rs -> rs.getString(1), accountIds.toArray()));
    }

    @Override
    public List<AlertLabel> launderingLabels() {
        // MAX over the labels is 1 if any transaction is laundering; COUNT skips transactions without a label
        return Jdbc.query(db, "SELECT a.seq, a.rule_id, a.created_at, COUNT(t.laundering_label), MAX(t.laundering_label) "
                + "FROM alerts a LEFT JOIN alert_transactions x ON x.alert_seq = a.seq "
                + "LEFT JOIN transactions t ON t.id = x.transaction_id GROUP BY a.seq ORDER BY a.seq",
                rs -> new AlertLabel(String.format("ALR-%05d", rs.getLong(1)), rs.getString(2),
                        Instant.ofEpochMilli(rs.getLong(3)), rs.getInt(4) == 0 ? null : rs.getInt(5) == 1));
    }

    @Override
    public boolean hasOpenAlert(RuleMatch match) {
        String placeholders = String.join(",", match.transactions().stream().map(t -> "?").toList());
        Object[] params = new Object[3 + match.transactions().size()];
        params[0] = match.ruleId();
        params[1] = match.accountId();
        params[2] = AlertStatus.CLOSED.name();
        for (int i = 0; i < match.transactions().size(); i++) {
            params[3 + i] = match.transactions().get(i).id();
        }
        return !Jdbc.query(db, "SELECT 1 FROM alerts a JOIN alert_transactions t ON t.alert_seq = a.seq "
                + "WHERE a.rule_id = ? AND a.account_id = ? AND a.status <> ? AND t.transaction_id IN ("
                + placeholders + ") LIMIT 1", rs -> 1, params).isEmpty();
    }

    @Override
    public Optional<Alert> findById(String id) {
        if (!id.matches("ALR-\\d+")) {
            return Optional.empty();
        }
        return Jdbc.query(db, SELECT + "WHERE a.seq = ?", SqliteAlertRepository::map,
                Long.parseLong(id.substring(4))).stream().findFirst();
    }

    @Override
    public void updateStatus(String id, AlertStatus status) {
        if (findById(id).isEmpty()) {
            throw new IllegalArgumentException("No alert " + id);
        }
        Jdbc.update(db, "UPDATE alerts SET status = ? WHERE seq = ?", status.name(), Long.parseLong(id.substring(4)));
    }

    @Override
    public void updatePriority(String id, int priority) {
        if (findById(id).isEmpty()) {
            throw new IllegalArgumentException("No alert " + id);
        }
        Jdbc.update(db, "UPDATE alerts SET priority = ? WHERE seq = ?", priority, Long.parseLong(id.substring(4)));
    }

    private static Alert map(ResultSet rs) throws SQLException {
        String ids = rs.getString(4);
        return new Alert(String.format("ALR-%05d", rs.getLong(1)), rs.getString(2), rs.getString(3),
                ids == null ? List.of() : Arrays.stream(ids.split(",")).sorted().toList(),
                new BigDecimal(rs.getString(5)), rs.getInt(6), rs.getInt(7), AlertStatus.valueOf(rs.getString(8)),
                Instant.ofEpochMilli(rs.getLong(9)));
    }
}
