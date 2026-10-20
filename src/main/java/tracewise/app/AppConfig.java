package tracewise.app;

import java.nio.file.Path;
import tracewise.domain.ActorType;

/** userRole is ANALYST or SUPERVISOR: TraceWise has no login, so the session states who is working. */
public record AppConfig(Path databasePath, String userName, ActorType userRole) {
}
