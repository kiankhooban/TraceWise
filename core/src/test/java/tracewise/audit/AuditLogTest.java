package tracewise.audit;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tracewise.agent.AgentOutcome;
import tracewise.agent.OutcomeStatus;
import tracewise.agent.TraceStep;
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

    @Test
    void agentStepsAndOutcomesAreAuditedAgainstTheirSubject() {
        try (var db = Database.open(dir.resolve("a.db"))) {
            var log = new AuditLog(new SqliteAuditRepository(db));
            log.onStep(new TraceStep("ALR-00001", 1, "account_history", "{\"account_id\":\"C1\"}", "3 rows", false, 12));
            log.onStep(new TraceStep("ALR-00001", 2, "account_history", "{}", "missing account_id", true, 0));
            log.onFinished(new AgentOutcome("ALR-00001", OutcomeStatus.COMPLETED, "VERDICT: ESCALATE"));

            var entries = log.query(new AuditFilter(null, "ALR-00001"));
            assertEquals(List.of("AGENT_STEP", "AGENT_STEP", "AGENT_FINISHED"), entries.stream().map(AuditEntry::eventType).toList());
            assertEquals(List.of("OK", "ERROR", "COMPLETED"), entries.stream().map(AuditEntry::outcome).toList());
            assertEquals(ActorType.AGENT, entries.get(0).actor().type());
            assertEquals("account_history {\"account_id\":\"C1\"}", entries.get(0).details());
        }
    }

    @Test
    void entriesAreFilteredByDateRangeAndKindOfActor() {
        try (var db = Database.open(dir.resolve("a.db"))) {
            var log = new AuditLog(new SqliteAuditRepository(db));
            var kian = new tracewise.domain.Actor("kian", tracewise.domain.ActorType.ANALYST);
            log.append(new AuditEntry(java.time.Instant.parse("2026-09-01T10:00:00Z"), kian, "IMPORT", "a.csv", "d", "OK"));
            log.append(new AuditEntry(java.time.Instant.parse("2026-09-02T10:00:00Z"), tracewise.domain.Actor.AGENT,
                    "AGENT_STEP", "ALR-00001", "d", "OK"));
            log.append(new AuditEntry(java.time.Instant.parse("2026-09-03T10:00:00Z"), kian, "IMPORT", "b.csv", "d", "OK"));

            var september2 = new AuditFilter(null, null, java.time.Instant.parse("2026-09-02T00:00:00Z"),
                    java.time.Instant.parse("2026-09-03T00:00:00Z"), null);
            assertEquals(List.of("ALR-00001"), log.query(september2).stream().map(AuditEntry::target).toList());
            var people = new AuditFilter(null, null, null, null, tracewise.domain.ActorType.ANALYST);
            assertEquals(List.of("a.csv", "b.csv"), log.query(people).stream().map(AuditEntry::target).toList());
        }
    }
}
