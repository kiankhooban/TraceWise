package tracewise.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tracewise.audit.AuditFilter;
import tracewise.domain.ActorType;
import tracewise.llm.ModelConfig;
import tracewise.tools.ScenarioGenerator;

/** F08: the audit trail through the facade, as the GUI's Audit Log screen reads it. */
class AuditQueryTest {

    @TempDir Path dir;

    @Test
    void theFacadeFiltersTheAuditTrailByEventType() throws Exception {
        try (var f = TraceWiseBootstrap.create(new AppConfig(dir.resolve("t.db"), "kian", ActorType.ANALYST,
                ModelConfig.of("anthropic", "claude-haiku-5-5", false), dir.resolve("replays"), dir.resolve(".env")),
                () -> null)) {
            f.importTransactions(Files.write(dir.resolve("s.csv"), ScenarioGenerator.generate(42)));
            f.runMonitoring();

            var imports = f.queryAudit(new AuditFilter("IMPORT", null));
            assertEquals(1, imports.size());
            assertEquals("kian", imports.get(0).actor().name());
            assertTrue(f.queryAudit(AuditFilter.ALL).size() >= 2);
        }
    }
}
