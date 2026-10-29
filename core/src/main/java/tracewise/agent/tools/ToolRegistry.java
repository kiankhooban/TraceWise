package tracewise.agent.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import tracewise.llm.ToolCall;
import tracewise.persistence.ExactJson;
import tracewise.llm.ToolParameter;
import tracewise.llm.ToolSchema;

/**
 * The only path from the agent to a tool. Every call is validated against the tool's schema before it
 * runs, and every problem (unknown tool, bad arguments, tool failure) goes back to the agent as an
 * error result it can correct, never as an exception that ends the investigation.
 */
public class ToolRegistry {

    private static final ObjectMapper JSON = ExactJson.mapper();

    private final Map<String, AgentTool> tools = new LinkedHashMap<>();

    public void register(AgentTool tool) {
        tools.put(tool.name(), tool);
    }

    public List<ToolSchema> schemas(Set<String> names) {
        var result = new ArrayList<ToolSchema>();
        for (AgentTool tool : tools.values()) {
            if (names.contains(tool.name())) {
                result.add(tool.schema());
            }
        }
        if (result.size() != names.size()) {
            throw new IllegalArgumentException("Unregistered tools requested: " + names + ", registered: " + tools.keySet());
        }
        return result;
    }

    public ToolResult execute(ToolCall call) {
        AgentTool tool = tools.get(call.name());
        if (tool == null) {
            return ToolResult.error("Unknown tool '" + call.name() + "'. Available tools: " + tools.keySet());
        }
        ObjectNode args;
        try {
            JsonNode parsed = JSON.readTree(call.argumentsJson() == null || call.argumentsJson().isBlank() ? "{}" : call.argumentsJson());
            if (!(parsed instanceof ObjectNode object)) {
                return ToolResult.error("Arguments must be a JSON object");
            }
            args = object;
        } catch (JsonProcessingException e) {
            return ToolResult.error("Arguments are not valid JSON: " + e.getOriginalMessage());
        }
        String problem = validate(tool.schema(), args);
        if (problem != null) {
            return ToolResult.error("Invalid arguments for " + tool.name() + ": " + problem);
        }
        try {
            return tool.execute(new ToolArguments(args));
        } catch (RuntimeException e) {
            return ToolResult.error(tool.name() + " failed: " + e.getMessage());
        }
    }

    /** Returns what is wrong with the arguments, or null if they match the schema. Null values count as absent. */
    private static String validate(ToolSchema schema, ObjectNode args) {
        args.properties().removeIf(e -> e.getValue().isNull());
        var declared = new LinkedHashMap<String, ToolParameter>();
        schema.parameters().forEach(p -> declared.put(p.name(), p));
        for (var entry : args.properties()) {
            if (!declared.containsKey(entry.getKey())) {
                return "unknown argument '" + entry.getKey() + "'; expected " + declared.keySet();
            }
        }
        for (ToolParameter p : schema.parameters()) {
            JsonNode value = args.get(p.name());
            if (value == null) {
                if (p.required()) {
                    return "missing required argument '" + p.name() + "'";
                }
                continue;
            }
            boolean ok = switch (p.type()) {
                case STRING -> value.isTextual();
                case INTEGER -> value.isIntegralNumber() && value.canConvertToInt();
                case NUMBER -> value.isNumber();
                case BOOLEAN -> value.isBoolean();
            };
            if (!ok) {
                return "'" + p.name() + "' must be " + p.type().name().toLowerCase();
            }
        }
        return null;
    }
}
