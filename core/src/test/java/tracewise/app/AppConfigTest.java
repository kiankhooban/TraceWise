package tracewise.app;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tracewise.domain.ActorType;

class AppConfigTest {

    @Test
    void readsTheSameSettingsAsTheCli() {
        var c = AppConfig.fromEnvironment(Map.of("TRACEWISE_DB", "/tmp/x.db", "TRACEWISE_ROLE", "supervisor",
                "TRACEWISE_LLM", "replay", "TRACEWISE_MODEL", "claude-haiku-5-5", "TRACEWISE_HOME", "/opt/tw"), "kian");

        assertEquals(Path.of("/tmp/x.db"), c.databasePath());
        assertEquals("kian", c.userName());
        assertEquals(ActorType.SUPERVISOR, c.userRole());
        assertEquals("replay", c.model().provider());
        assertEquals("claude-haiku-5-5", c.model().modelName());
        assertEquals(Path.of("/opt/tw/data/replays"), c.replayDir());
        assertEquals(Path.of("/opt/tw/.env"), c.envFile());
    }

    @Test
    void defaultsMatchTheCli() {
        var c = AppConfig.fromEnvironment(Map.of(), "kian");

        assertEquals(Path.of("tracewise.db"), c.databasePath());
        assertEquals(ActorType.ANALYST, c.userRole());
        assertEquals("anthropic", c.model().provider());
        assertEquals("claude-sonnet-5-5", c.model().modelName());
        assertEquals(false, c.model().record());
    }
}
