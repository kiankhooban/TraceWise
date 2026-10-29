package tracewise.app;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EnvFileTest {

    @TempDir Path dir;

    @Test
    void readsNameValueLinesAndSkipsCommentsAndBlanks() throws IOException {
        var file = Files.write(dir.resolve(".env"), List.of("# comment", "", "ANTHROPIC_API_KEY=sk-ant-abc", "MODEL=x=y"));
        assertEquals(Map.of("ANTHROPIC_API_KEY", "sk-ant-abc", "MODEL", "x=y"), EnvFile.read(file));
    }

    @Test
    void malformedLinesAreReportedByNumberWithoutTheirContent() throws IOException {
        var file = Files.write(dir.resolve(".env"), List.of("# comment", "sk-ant-secret-value", "OK=1"));
        var e = assertThrows(IllegalArgumentException.class, () -> EnvFile.read(file));
        assertEquals(".env line 2 is not NAME=value", e.getMessage());
        assertFalse(e.getMessage().contains("secret"));
    }

    @Test
    void aMissingFileIsEmpty() {
        assertEquals(Map.of(), EnvFile.read(dir.resolve("absent")));
    }
}
