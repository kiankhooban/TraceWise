package tracewise.agent.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import tracewise.agent.TraceStep;
import tracewise.llm.ToolParameter;
import tracewise.llm.ToolSchema;
import tracewise.retrieval.RetrievedPassage;
import tracewise.retrieval.Retriever;

/** Read tool (F04): FINTRAC money-laundering indicators that match a pattern. The context of the Retriever strategy. */
public class SearchIndicatorsTool implements AgentTool {

    public static final String NAME = "search_indicators";
    static final int MAX_RESULTS = 10;
    private static final ObjectMapper JSON = new ObjectMapper();

    private final Retriever retriever;

    public SearchIndicatorsTool(Retriever retriever) {
        this.retriever = retriever;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public ToolSchema schema() {
        return new ToolSchema(name(), "FINTRAC's published money-laundering indicators that best match a description "
                + "of a pattern, each with its similarity score (0 to 1).", List.of(
                new ToolParameter("query", ToolParameter.Type.STRING, "The pattern in FINTRAC's indicator vocabulary, "
                        + "for example 'deposits below the reporting threshold', not raw figures", true),
                new ToolParameter("max_results", ToolParameter.Type.INTEGER, "How many to return (1 to 10, default 5)",
                        false)));
    }

    @Override
    public ToolResult execute(ToolArguments args) {
        String query = args.string("query");
        int n = Math.max(1, Math.min(MAX_RESULTS, args.integer("max_results", 5)));
        var result = new LinkedHashMap<String, Object>();
        result.put("query", query);
        var rows = retriever.search(query, n).stream().map(SearchIndicatorsTool::row).toList();
        result.put("indicators", rows);
        if (rows.isEmpty()) {
            result.put("message", "No indicator matches this description closely enough. Do not cite an indicator "
                    + "for it: describe the pattern in other words or continue without one.");
        }
        return ToolResult.json(result);
    }

    /** Every indicator this tool returned in the run's successful steps. */
    public static Set<String> returnedIndicators(List<TraceStep> steps) {
        var returned = new HashSet<String>();
        for (TraceStep step : steps) {
            if (step.toolName().equals(NAME) && !step.error()) {
                try {
                    JSON.readTree(step.result()).path("indicators").forEach(i -> returned.add(i.path("indicator").asText()));
                } catch (JsonProcessingException e) {
                    throw new IllegalStateException("unreadable " + NAME + " result", e);
                }
            }
        }
        return returned;
    }

    private static Map<String, Object> row(RetrievedPassage p) {
        var row = new LinkedHashMap<String, Object>();
        row.put("indicator", p.text());
        row.put("source", p.source());
        row.put("score", p.score());
        return row;
    }
}
