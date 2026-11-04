package tracewise.ui.gui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import tracewise.agent.AssistantContext;
import tracewise.agent.TraceStep;
import tracewise.agent.tools.QueryAlertsTool;
import tracewise.agent.tools.QueryCasesTool;

/**
 * F10: the results of a search request as a table. The assistant's query tools return validated rows as JSON;
 * the table shows the last search of the answer's run, exactly as the tool returned it.
 */
record SearchResults(String title, List<String> columns, List<List<String>> rows) {

    private static final ObjectMapper JSON = new ObjectMapper();
    /** The query tool and the key of its rows. */
    private static final Map<String, String> TOOLS = Map.of(QueryAlertsTool.NAME, "alerts", QueryCasesTool.NAME, "cases");

    static Optional<SearchResults> from(List<TraceStep> steps) {
        for (int i = steps.size() - 1; i >= 0; i--) {
            TraceStep s = steps.get(i);
            String key = TOOLS.get(s.toolName());
            if (key != null && !s.error()) {
                return parse(s.result(), key);
            }
        }
        return Optional.empty();
    }

    private static Optional<SearchResults> parse(String json, String key) {
        JsonNode root;
        try {
            root = JSON.readTree(json);
        } catch (java.io.IOException e) {
            return Optional.empty(); // not the tool's JSON: no table rather than a wrong one
        }
        JsonNode items = root.path(key);
        var columns = new ArrayList<String>();
        var fields = new ArrayList<String>();
        if (items.size() > 0) {
            items.get(0).fieldNames().forEachRemaining(f -> {
                fields.add(f);
                columns.add(f.replace('_', ' '));
            });
        }
        var rows = new ArrayList<List<String>>();
        for (JsonNode item : items) {
            rows.add(fields.stream().map(f -> text(item.path(f))).toList());
        }
        int count = root.path("count").asInt(rows.size());
        String noun = count == 1 ? key.substring(0, key.length() - 1) : key; // "1 case", "2 cases"
        // the tools list at most 50 rows: say so rather than a count the table does not show
        String title = rows.size() < count ? rows.size() + " of " + count + " " + key : count + " " + noun;
        String message = root.path("message").asText("");
        return Optional.of(new SearchResults(message.isEmpty() ? title : title + ". " + message, columns, rows));
    }

    private static String text(JsonNode value) {
        if (value.isNull() || value.isMissingNode()) {
            return "";
        }
        if (value.isArray()) {
            var parts = new ArrayList<String>();
            // a nested object (a case's alerts) reads as its first field, its id
            value.forEach(v -> parts.add(v.isObject() && v.size() > 0 ? v.elements().next().asText() : v.asText()));
            return String.join(" ", parts);
        }
        return value.asText();
    }

    /** What the panel sent with a question, so the analyst sees which case or account the answer is about. */
    static String contextLabel(AssistantContext c) {
        if (c.caseId() != null) {
            return "context " + c.caseId();
        }
        return c.accountId() != null ? "context account " + c.accountId() : "no context";
    }
}
