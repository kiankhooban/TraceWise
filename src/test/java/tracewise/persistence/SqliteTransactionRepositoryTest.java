package tracewise.persistence;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tracewise.domain.Account;
import tracewise.domain.DateRange;
import tracewise.domain.PaymentChannel;
import tracewise.domain.Transaction;

class SqliteTransactionRepositoryTest {

    @TempDir Path dir;
    Database db;
    SqliteTransactionRepository repo;

    @BeforeEach
    void open() {
        db = Database.open(dir.resolve("t.db"));
        repo = new SqliteTransactionRepository(db);
    }

    @AfterEach
    void close() {
        db.close();
    }

    static Transaction tx(String id, String at, String from, String to, String cad) {
        return new Transaction(id, Instant.parse(at), from, to, new BigDecimal(cad), new BigDecimal(cad), "CAD",
                PaymentChannel.WIRE, null, null);
    }

    @Test
    void savedTransactionsReadBackExactly() {
        var t = new Transaction("T1", Instant.parse("2026-09-01T10:15:00Z"), "CASH", "C1",
                new BigDecimal("9500.10"), new BigDecimal("6670.00"), "USD", PaymentChannel.CASH, "AE", Boolean.TRUE);
        repo.saveAll(List.of(t));
        assertEquals(t, repo.findTransaction("T1").orElseThrow());
        assertTrue(repo.exists("T1"));
        assertFalse(repo.exists("T2"));
        assertTrue(repo.findTransaction("T2").isEmpty());
    }

    @Test
    void transactionsForAnAccountAreFilteredByRangeAndOrderedByTime() {
        repo.saveAll(List.of(
                tx("b", "2026-09-02T00:00:00Z", "A", "B", "2"),
                tx("a", "2026-09-01T00:00:00Z", "B", "A", "1"),
                tx("c", "2026-09-05T00:00:00Z", "A", "C", "3"),
                tx("x", "2026-09-01T12:00:00Z", "C", "D", "9")));
        var all = repo.transactionsFor("A", DateRange.ALL);
        assertEquals(List.of("a", "b", "c"), all.stream().map(Transaction::id).toList());
        var range = new DateRange(Instant.parse("2026-09-01T00:00:00Z"), Instant.parse("2026-09-05T00:00:00Z"));
        assertEquals(List.of("a", "b"), repo.transactionsFor("A", range).stream().map(Transaction::id).toList());
    }

    @Test
    void savingAnExistingAccountKeepsItsWatchlistFlag() {
        repo.saveAccount(new Account("A", true));
        repo.saveAccount(new Account("A", false));
        repo.saveAccount(new Account("B", false));
        assertEquals(List.of(new Account("A", true), new Account("B", false)), repo.accounts());
    }

    @Test
    void failedTransactionLeavesNoPartialData() {
        assertThrows(IllegalStateException.class, () -> db.inTransaction(() -> {
            repo.saveAll(List.of(tx("p", "2026-09-01T00:00:00Z", "A", "B", "1")));
            throw new IllegalStateException("disk full");
        }));
        assertFalse(repo.exists("p"));
    }

    @Test
    void usesWriteAheadLogging() {
        assertEquals("wal", db.journalMode());
    }

    @Test
    void savingTransactionsCreatesAccountsOnlyForPartiesInsideTheBank() {
        repo.saveAll(List.of(tx("1", "2026-09-01T00:00:00Z", "CASH", "C1", "5"),
                tx("2", "2026-09-01T01:00:00Z", "C1", "C2", "5"),
                tx("3", "2026-09-01T02:00:00Z", "EXT-US-1", "C2", "5")));
        assertEquals(List.of(new Account("C1", false), new Account("C2", false)), repo.accounts());
    }

    @Test
    void saveAllIsAllOrNothing() {
        repo.saveAll(List.of(tx("1", "2026-09-01T00:00:00Z", "A", "B", "5")));
        assertThrows(DatabaseException.class, () -> repo.saveAll(List.of(
                tx("2", "2026-09-01T00:00:00Z", "A", "C", "5"), tx("1", "2026-09-01T00:00:00Z", "A", "B", "5"))));
        assertFalse(repo.exists("2"));
        assertEquals(List.of("A", "B"), repo.accounts().stream().map(Account::id).toList());
    }
}
