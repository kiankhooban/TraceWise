package tracewise.app;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads a .env file as plain NAME=value text. It is never executed, and a bad line is reported by
 * its number only, so a secret on a malformed line is never echoed.
 */
public final class EnvFile {

    private EnvFile() {
    }

    public static Map<String, String> read(Path file) {
        if (!Files.exists(file)) {
            return Map.of();
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + file.getFileName(), e);
        }
        var values = new HashMap<String, String>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int eq = line.indexOf('=');
            if (eq <= 0 || !line.substring(0, eq).matches("[A-Z_][A-Z0-9_]*")) {
                throw new IllegalArgumentException(file.getFileName() + " line " + (i + 1) + " is not NAME=value");
            }
            values.put(line.substring(0, eq), line.substring(eq + 1));
        }
        return values;
    }
}
