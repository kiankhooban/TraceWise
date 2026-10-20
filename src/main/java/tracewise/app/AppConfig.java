package tracewise.app;

import java.nio.file.Path;

public record AppConfig(Path databasePath, String analystName) {
}
