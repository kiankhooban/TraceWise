package tracewise.app;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tracewise.audit.AuditFilter;
import tracewise.audit.AuditLog;
import tracewise.audit.SqliteAuditRepository;
import tracewise.domain.Actor;
import tracewise.domain.ActorType;
import tracewise.domain.Transaction;
import tracewise.ingest.CurrencyConverter;
import tracewise.ingest.IbmCsvFormat;
import tracewise.ingest.ImportException;
import tracewise.ingest.ImportSummary;
import tracewise.ingest.RejectedRow;
import tracewise.ingest.ScenarioCsvFormat;
import tracewise.persistence.Database;
import tracewise.persistence.DatabaseException;
import tracewise.persistence.SqliteTransactionRepository;

class ImportServiceTest {

    static final String HEADER = "transaction_id,timestamp,sender_bank,sender_account,receiver_bank,"
            + "receiver_account,amount,currency,channel,counterparty_country,is_laundering";

    @TempDir Path dir;
    Database db;
    SqliteTransactionRepository repo;
    AuditLog audit;
    ImportService service;

    @BeforeEach
    void setUp() {
        db = Database.open(dir.resolve("t.db"));
        repo = new SqliteTransactionRepository(db);
        audit = new AuditLog(new SqliteAuditRepository(db));
        service = newService(repo);
    }

    ImportService newService(SqliteTransactionRepository repository) {
        var rates = CurrencyConverter.bankOfCanadaDefaults();
        return new ImportService(repository, List.of(new IbmCsvFormat(rates), new ScenarioCsvFormat(rates)), audit,
                new Actor("kian", ActorType.ANALYST));
    }

    @AfterEach
    void tearDown() {
        db.close();
    }

    Path csv(String... lines) throws IOException {
        return Files.write(dir.resolve("in.csv"), List.of(lines));
    }

    @Test
    void previewNamesTheFormatAndShowsTheFirstRows() throws IOException {
        var file = csv(HEADER, "T1,2026-09-01T10:00:00,,CASH,001,C1,9500,CAD,CASH,CA,",
                "T2,2026-09-01T11:00:00,001,C1,001,C2,100,CAD,WIRE,CA,");
        var preview = service.preview(file);
        assertEquals("TraceWise scenario CSV", preview.formatName());
        assertEquals(2, preview.firstRows().size());
        assertEquals("T1", preview.firstRows().get(0).get(0));
    }

    @Test
    void unrecognisedFormatIsRefusedListingTheExpectedColumns() throws IOException {
        var file = csv("date,amount", "2026-09-01,5");
        var e = assertThrows(ImportException.class, () -> service.preview(file));
        assertTrue(e.getMessage().startsWith("unrecognised file format"), e.getMessage());
        assertTrue(e.getMessage().contains("transaction_id,timestamp"), e.getMessage());
        assertTrue(e.getMessage().contains("Timestamp,From Bank,Account"), e.getMessage());
        assertThrows(ImportException.class, () -> service.importFile(file));
    }

    @Test
    void importStoresValidRowsAndReportsEachRejectedRow() throws IOException {
        var file = csv(HEADER,
                "T1,2026-09-01T10:00:00,,CASH,001,C1,9500,CAD,CASH,CA,",
                "T2,2026-09-03T11:00:00,001,C1,002,C2,100,USD,WIRE,US,0",
                "T3,2026-09-02T11:00:00,001,C1,002,C2,-4,CAD,WIRE,CA,",
                "",
                "T4,2026-09-02T12:00:00,001,C1,002,C2,4,RUB,WIRE,CA,");
        ImportSummary s = service.importFile(file);

        assertEquals(4, s.rowsRead());
        assertEquals(2, s.rowsImported());
        assertEquals(List.of(new RejectedRow(4, "amount must be positive"),
                new RejectedRow(6, "currency not in rate table: RUB")), s.rejected());
        assertEquals(2, s.accounts());
        assertEquals(Instant.parse("2026-09-01T10:00:00Z"), s.earliest());
        assertEquals(Instant.parse("2026-09-03T11:00:00Z"), s.latest());
        assertTrue(repo.exists("T1") && repo.exists("T2"));

        var entries = audit.query(new AuditFilter("IMPORT", "in.csv"));
        assertEquals(1, entries.size());
        assertEquals("OK", entries.get(0).outcome());
        assertEquals("2 of 4 rows imported, 2 rejected", entries.get(0).details());
    }

    @Test
    void duplicateIdsAreSkippedAndReportedWhetherStoredOrRepeatedInTheFile() throws IOException {
        var file = csv(HEADER, "T1,2026-09-01T10:00:00,,CASH,001,C1,9500,CAD,CASH,CA,",
                "T1,2026-09-01T10:00:00,,CASH,001,C1,9500,CAD,CASH,CA,");
        var first = service.importFile(file);
        assertEquals(1, first.rowsImported());
        assertEquals(List.of(new RejectedRow(3, "duplicate transaction id: T1")), first.rejected());

        var again = service.importFile(file);
        assertEquals(0, again.rowsImported());
        assertEquals(2, again.rejected().size());
    }

    @Test
    void databaseFailureLeavesNothingBehindAndIsAudited() throws IOException {
        var failing = new SqliteTransactionRepository(db) {
            @Override
            public void saveAll(List<Transaction> transactions) {
                throw new DatabaseException(new java.sql.SQLException("disk I/O error"));
            }
        };
        var file = csv(HEADER, "T1,2026-09-01T10:00:00,,CASH,001,C1,9500,CAD,CASH,CA,");
        assertThrows(DatabaseException.class, () -> newService(failing).importFile(file));
        assertFalse(repo.exists("T1"));
        assertEquals("FAILED", audit.query(new AuditFilter("IMPORT", "in.csv")).get(0).outcome());
    }

    @Test
    void unreadableFileIsReported() {
        var e = assertThrows(ImportException.class, () -> service.importFile(dir.resolve("missing.csv")));
        assertTrue(e.getMessage().startsWith("cannot read file"), e.getMessage());
    }
}
