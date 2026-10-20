package tracewise.llm;

import java.util.List;

/** What the model is told about one tool: its name, purpose and parameters. */
public record ToolSchema(String name, String description, List<ToolParameter> parameters) {

    public ToolSchema {
        parameters = List.copyOf(parameters);
    }
}
