package tracewise.app;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tracewise.domain.Case;
import tracewise.persistence.Database;
import tracewise.persistence.SqliteCaseRepository;

class CaseServiceTest {

    @TempDir Path dir;

    @Test
    void listsEveryCaseByItsId() {
        try (var db = Database.open(dir.resolve("c.db"))) {
            var cases = new SqliteCaseRepository(db);
            Instant t = Instant.parse("2026-09-01T00:00:00Z");
            cases.save(new Case("CAS-00002", t, List.of("ALR-00002")));
            cases.save(new Case("CAS-00001", t, List.of("ALR-00001")));

            assertEquals(List.of("CAS-00001", "CAS-00002"),
                    new CaseService(cases, null, null, null, null).listCases().stream().map(Case::id).toList());
        }
    }
}
