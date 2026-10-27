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
import java.util.Set;

/**
 * Recorded model answers, one JSON file per request in a directory. A request is identified by the
 * SHA-256 of its JSON form, so a replay matches only an identical conversation, except for the results of
 * the unmatched tools: their content is left out of the identity (the file still stores it).
 */
public class ReplayStore {

    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private final Path dir;
    private final Set<String> unmatchedTools;

    public ReplayStore(Path dir) {
        this(dir, Set.of());
    }

    /**
     * unmatchedTools: tools whose results may differ between machines for the same data, such as local
     * embedding search (its scores differ in the third decimal between ARM and x86 CPUs).
     */
    public ReplayStore(Path dir, Set<String> unmatchedTools) {
        this.dir = dir;
        this.unmatchedTools = Set.copyOf(unmatchedTools);
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

    private LLMRequest identity(LLMRequest request) {
        if (unmatchedTools.isEmpty()) {
            return request;
        }
        return new LLMRequest(request.systemPrompt(), request.messages().stream()
                .map(m -> m instanceof LLMMessage.ToolResult r && unmatchedTools.contains(r.toolName())
                        ? new LLMMessage.ToolResult(r.callId(), r.toolName(), "") : m)
                .toList(), request.tools());
    }

    private Path fileFor(LLMRequest request) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(JSON.writeValueAsBytes(identity(request)));
            return dir.resolve(HexFormat.of().formatHex(hash) + ".json");
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
