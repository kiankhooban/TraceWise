package tracewise.agent.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tracewise.llm.ToolParameter;
import tracewise.llm.ToolSchema;
import tracewise.retrieval.CaseMemory;
import tracewise.retrieval.RetrievedPassage;

/** Read tool (F04): closed and escalated cases with a similar pattern, and how they were decided. */
public class SimilarCasesTool implements AgentTool {

    public static final String NAME = "similar_cases";

    private final CaseMemory memory;

    public SimilarCasesTool(CaseMemory memory) {
        this.memory = memory;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public ToolSchema schema() {
        return new ToolSchema(name(), "Past cases that were closed or escalated with a pattern similar to a "
                + "description, each with its decision, a summary and a similarity score (0 to 1).", List.of(
                new ToolParameter("description", ToolParameter.Type.STRING,
                        "The pattern, in the same words as for search_indicators", true)));
    }

    @Override
    public ToolResult execute(ToolArguments args) {
        String description = args.string("description");
        var result = new LinkedHashMap<String, Object>();
        result.put("description", description);
        result.put("cases", memory.similar(description, 3).stream().map(SimilarCasesTool::row).toList());
        if (((List<?>) result.get("cases")).isEmpty()) {
            result.put("message", "No closed or escalated case is similar enough, or none has been decided yet.");
        }
        return ToolResult.json(result);
    }

    private static Map<String, Object> row(RetrievedPassage p) {
        var row = new LinkedHashMap<String, Object>();
        row.put("case_id", p.source());
        row.put("summary", p.text());
        row.put("score", p.score());
        return row;
    }
}
