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
import tracewise.domain.Alert;
import tracewise.domain.AlertStatus;
import tracewise.domain.PaymentChannel;
import tracewise.domain.Transaction;
import tracewise.metrics.AlertLabel;

/** F12 ground truth per alert: its own transactions' labels and whether its account launders anywhere. */
class SqliteAlertRepositoryTest {

    static final Instant T0 = Instant.parse("2026-09-01T00:00:00Z");

    @TempDir Path dir;
    Database db;
    SqliteAlertRepository alerts;

    @BeforeEach
    void open() {
        db = Database.open(dir.resolve("a.db"));
        alerts = new SqliteAlertRepository(db);
    }

    @AfterEach
    void close() {
        db.close();
    }

    static Transaction tx(String id, String from, String to, Boolean laundering) {
        return new Transaction(id, T0, from, to, BigDecimal.TEN, BigDecimal.TEN, "CAD", PaymentChannel.WIRE, "CA",
                laundering);
    }

    void alert(String account, String... transactionIds) {
        alerts.save(new Alert(null, "R4", account, List.of(transactionIds), BigDecimal.TEN, 50, 2, AlertStatus.NEW, T0));
    }

    /** Review 10: the account's label must count laundering it received as well as laundering it sent. */
    @Test
    void theAccountLabelCoversBothSidesAndIsUnknownWithoutLabels() {
        new SqliteTransactionRepository(db).saveAll(List.of(tx("t1", "A", "X", false), tx("t2", "Y", "A", true),
                tx("t3", "B", "Z", false), tx("t4", "C", "Z", null), tx("t5", "D", "Z", true)));
        alert("A", "t1"); // clean alert, but A received laundering
        alert("B", "t3"); // clean alert on a clean account
        alert("C", "t4"); // no labels at all
        alert("D", "t5"); // laundering alert

        List<AlertLabel> labels = alerts.launderingLabels();

        assertEquals(new AlertLabel("ALR-00001", "R4", T0, false, true), labels.get(0));
        assertEquals(new AlertLabel("ALR-00002", "R4", T0, false, false), labels.get(1));
        assertEquals(new AlertLabel("ALR-00003", "R4", T0, null, null), labels.get(2));
        assertEquals(new AlertLabel("ALR-00004", "R4", T0, true, true), labels.get(3));
    }
}
