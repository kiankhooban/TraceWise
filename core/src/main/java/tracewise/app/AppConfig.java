package tracewise.app;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import tracewise.domain.ActorType;
import tracewise.llm.ModelConfig;

/**
 * userRole is ANALYST or SUPERVISOR: TraceWise has no login, so the session states who is working.
 * envFile is the .env holding ANTHROPIC_API_KEY; it is read only when the model is called.
 */
public record AppConfig(Path databasePath, String userName, ActorType userRole, ModelConfig model, Path replayDir,
        Path envFile) {

    public static final String DEFAULT_DB = "tracewise.db";
    public static final String DEFAULT_ROLE = "ANALYST";
    public static final String DEFAULT_PROVIDER = "anthropic";
    public static final String DEFAULT_MODEL = "claude-sonnet-5-5";

    /** The settings the CLI reads from its environment variables, with the same defaults (used by the GUI). */
    public static AppConfig fromEnvironment(Map<String, String> env, String userName) {
        Path home = Path.of(env.getOrDefault("TRACEWISE_HOME", "."));
        return new AppConfig(Path.of(env.getOrDefault("TRACEWISE_DB", DEFAULT_DB)), userName,
                ActorType.valueOf(env.getOrDefault("TRACEWISE_ROLE", DEFAULT_ROLE).toUpperCase(Locale.ROOT)),
                ModelConfig.of(env.getOrDefault("TRACEWISE_LLM", DEFAULT_PROVIDER),
                        env.getOrDefault("TRACEWISE_MODEL", DEFAULT_MODEL),
                        Boolean.parseBoolean(env.getOrDefault("TRACEWISE_RECORD", "false"))),
                home.resolve("data/replays"), home.resolve(".env"));
    }
}
