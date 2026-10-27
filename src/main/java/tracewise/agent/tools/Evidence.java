package tracewise.agent.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import tracewise.agent.TraceStep;
import tracewise.retrieval.RetrievedPassage;

/** Reads back what the retrieval tools returned during a run, from its trace. */
final class Evidence {

    private static final ObjectMapper JSON = new ObjectMapper();

    private Evidence() {
    }

    /** The rows of list in each successful result of tool, each text once (best score kept), in first-seen order. */
    static List<RetrievedPassage> read(List<TraceStep> steps, String tool, String list, String textField, String sourceField) {
        var found = new LinkedHashMap<String, RetrievedPassage>();
        for (TraceStep step : steps) {
            if (!step.toolName().equals(tool) || step.error()) {
                continue;
            }
            try {
                JSON.readTree(step.result()).path(list).forEach(row -> {
                    var p = new RetrievedPassage(row.path(textField).asText(), row.path(sourceField).asText(),
                            row.path("score").asDouble());
                    found.merge(p.text(), p, (a, b) -> a.score() >= b.score() ? a : b);
                });
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("unreadable " + tool + " result", e);
            }
        }
        return new ArrayList<>(found.values());
    }
}
