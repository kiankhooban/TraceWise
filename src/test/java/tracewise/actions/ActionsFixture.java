package tracewise.actions;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import tracewise.audit.AuditLog;
import tracewise.audit.SqliteAuditRepository;
import tracewise.cases.state.CaseStatus;
import tracewise.domain.Alert;
import tracewise.domain.AlertStatus;
import tracewise.domain.Case;
import tracewise.domain.PaymentChannel;
import tracewise.domain.Transaction;
import tracewise.persistence.Database;
import tracewise.persistence.SqliteAlertRepository;
import tracewise.persistence.SqliteApprovalRepository;
import tracewise.persistence.SqliteCaseRepository;
import tracewise.persistence.SqliteTransactionRepository;

/** A database with accounts C1 to C3, alerts ALR-00001 (on C1, in case CAS-00001, under investigation) and ALR-00002. */
class ActionsFixture implements AutoCloseable {

    final Database db;
    final SqliteTransactionRepository accounts;
    final SqliteAlertRepository alerts;
    final SqliteCaseRepository cases;
    final AuditLog audit;
    final CommandContext ctx;
    final ApprovalQueue queue;
    final CommandDispatcher dispatcher;

    ActionsFixture(Path dir) {
        db = Database.open(dir.resolve("a.db"));
        accounts = new SqliteTransactionRepository(db);
        alerts = new SqliteAlertRepository(db);
        cases = new SqliteCaseRepository(db);
        audit = new AuditLog(new SqliteAuditRepository(db));
        Instant t = Instant.parse("2026-09-01T00:00:00Z");
        accounts.saveAll(List.of(
                new Transaction("t1", t, "C1", "C2", new BigDecimal("100"), new BigDecimal("100"), "CAD", PaymentChannel.WIRE, "CA", null),
                new Transaction("t2", t, "C3", "C1", new BigDecimal("200"), new BigDecimal("200"), "CAD", PaymentChannel.WIRE, "CA", null)));
        alerts.save(new Alert(null, "R4", "C1", List.of("t1"), new BigDecimal("100"), 60, 2, AlertStatus.IN_CASE, t));
        alerts.save(new Alert(null, "R5", "C3", List.of("t2"), new BigDecimal("200"), 40, 3, AlertStatus.NEW, t));
        cases.save(new Case("CAS-00001", t, List.of("ALR-00001"), CaseStatus.UNDER_INVESTIGATION));
        ctx = new CommandContext(cases, alerts, accounts);
        queue = new ApprovalQueue(db, new SqliteApprovalRepository(db), ctx, audit);
        dispatcher = new CommandDispatcher(db, ctx, new PermissionPolicy(), queue, audit);
    }

    CaseStatus caseStatus() {
        return cases.findById("CAS-00001").orElseThrow().status();
    }

    @Override
    public void close() {
        db.close();
    }
}
