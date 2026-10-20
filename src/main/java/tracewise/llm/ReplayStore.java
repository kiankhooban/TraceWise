package tracewise.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;

/**
 * Recorded model answers, one JSON file per request in a directory. A request is identified by the
 * SHA-256 of its JSON form, so a replay matches only an identical conversation.
 */
public class ReplayStore {

    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private final Path dir;

    public ReplayStore(Path dir) {
        this.dir = dir;
    }

    public void save(LLMRequest request, LLMResponse response) {
        try {
            Files.createDirectories(dir);
            Files.writeString(fileFor(request), JSON.writeValueAsString(Map.of("request", request, "response", response)),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot save recording in " + dir, e);
        }
    }

    public Optional<LLMResponse> find(LLMRequest request) {
        Path file = fileFor(request);
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(JSON.treeToValue(JSON.readTree(file.toFile()).get("response"), LLMResponse.class));
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read recording " + file.getFileName(), e);
        }
    }

    private Path fileFor(LLMRequest request) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(JSON.writeValueAsBytes(request));
            return dir.resolve(HexFormat.of().formatHex(hash) + ".json");
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
