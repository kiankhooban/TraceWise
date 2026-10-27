package tracewise.audit;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import tracewise.domain.Actor;
import tracewise.domain.ActorType;
import tracewise.persistence.Database;
import tracewise.persistence.DatabaseException;

public class SqliteAuditRepository implements AuditRepository {

    private final Database db;

    public SqliteAuditRepository(Database db) {
        this.db = db;
    }

    @Override
    public void append(AuditEntry e) {
        try (PreparedStatement ps = db.connection().prepareStatement("INSERT INTO audit_log "
                + "(ts, actor_name, actor_type, event_type, target, details, outcome) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            ps.setLong(1, e.timestamp().toEpochMilli());
            ps.setString(2, e.actor().name());
            ps.setString(3, e.actor().type().name());
            ps.setString(4, e.eventType());
            ps.setString(5, e.target());
            ps.setString(6, e.details());
            ps.setString(7, e.outcome());
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new DatabaseException(ex);
        }
    }

    @Override
    public List<AuditEntry> find(AuditFilter filter) {
        try (PreparedStatement ps = db.connection().prepareStatement("SELECT ts, actor_name, actor_type, "
                + "event_type, target, details, outcome FROM audit_log WHERE (?1 IS NULL OR event_type = ?1) "
                + "AND (?2 IS NULL OR target = ?2) AND (?3 IS NULL OR ts >= ?3) AND (?4 IS NULL OR ts < ?4) "
                + "AND (?5 IS NULL OR actor_type = ?5) ORDER BY seq")) {
            ps.setString(1, filter.eventType());
            ps.setString(2, filter.target());
            ps.setObject(3, filter.from() == null ? null : filter.from().toEpochMilli());
            ps.setObject(4, filter.to() == null ? null : filter.to().toEpochMilli());
            ps.setString(5, filter.actorType() == null ? null : filter.actorType().name());
            try (ResultSet rs = ps.executeQuery()) {
                var entries = new ArrayList<AuditEntry>();
                while (rs.next()) {
                    entries.add(new AuditEntry(Instant.ofEpochMilli(rs.getLong(1)),
                            new Actor(rs.getString(2), ActorType.valueOf(rs.getString(3))),
                            rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7)));
                }
                return entries;
            }
        } catch (SQLException ex) {
            throw new DatabaseException(ex);
        }
    }
}
