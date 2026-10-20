package tracewise.agent.tools;

import tracewise.llm.ToolSchema;

/** Something the agent can call. Arguments are validated against schema() before execute() runs. */
public interface AgentTool {

    String name();

    ToolSchema schema();

    ToolResult execute(ToolArguments args);
}
