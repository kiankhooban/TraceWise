package tracewise.audit;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tracewise.domain.Actor;
import tracewise.domain.ActorType;
import tracewise.persistence.Database;

class AuditLogTest {

    @TempDir Path dir;

    @Test
    void appendedEntriesAreQueryableByTypeAndTargetOldestFirst() {
        try (var db = Database.open(dir.resolve("a.db"))) {
            var log = new AuditLog(new SqliteAuditRepository(db));
            var analyst = new Actor("kian", ActorType.ANALYST);
            var first = new AuditEntry(Instant.parse("2026-10-20T10:00:00Z"), analyst, "IMPORT", "s.csv", "12 rows", "OK");
            var second = new AuditEntry(Instant.parse("2026-10-20T11:00:00Z"), Actor.SYSTEM, "MONITORING_RUN", "all", "", "OK");
            var third = new AuditEntry(Instant.parse("2026-10-20T12:00:00Z"), analyst, "IMPORT", "b.csv", "", "FAILED");
            log.append(first);
            log.append(second);
            log.append(third);

            assertEquals(List.of(first, second, third), log.query(AuditFilter.ALL));
            assertEquals(List.of(first, third), log.query(new AuditFilter("IMPORT", null)));
            assertEquals(List.of(third), log.query(new AuditFilter("IMPORT", "b.csv")));
        }
    }

    @Test
    void entriesCannotBeChangedOrDeleted() {
        try (var db = Database.open(dir.resolve("a.db"))) {
            new AuditLog(new SqliteAuditRepository(db)).append(
                    new AuditEntry(Instant.parse("2026-10-20T10:00:00Z"), Actor.SYSTEM, "IMPORT", "s.csv", "", "OK"));
            var c = db.connection();
            assertThrows(java.sql.SQLException.class, () -> c.createStatement().execute("DELETE FROM audit_log"));
            assertThrows(java.sql.SQLException.class,
                    () -> c.createStatement().execute("UPDATE audit_log SET outcome = 'FAILED'"));
        }
    }
}
