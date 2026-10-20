package tracewise.app;

import java.nio.file.Path;
import tracewise.domain.ActorType;
import tracewise.llm.ModelConfig;

/**
 * userRole is ANALYST or SUPERVISOR: TraceWise has no login, so the session states who is working.
 * envFile is the .env holding ANTHROPIC_API_KEY; it is read only when the model is called.
 */
public record AppConfig(Path databasePath, String userName, ActorType userRole, ModelConfig model, Path replayDir,
        Path envFile) {
}
