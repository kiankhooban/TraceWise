package tracewise.agent.tools;

import tracewise.llm.ToolSchema;

/**
 * Decorator: wraps a tool and makes it fail on purpose, so tests can check what the agent does when a tool fails
 * without changing any production tool.
 */
public class FaultInjectingTool implements AgentTool {

    /** NONE passes calls through; CRASH throws as a broken tool would; ERROR returns a failed result. */
    public enum FaultMode { NONE, CRASH, ERROR }

    private final AgentTool wrapped;
    private final FaultMode mode;

    public FaultInjectingTool(AgentTool wrapped, FaultMode mode) {
        this.wrapped = wrapped;
        this.mode = mode;
    }

    @Override
    public String name() {
        return wrapped.name();
    }

    @Override
    public ToolSchema schema() {
        return wrapped.schema();
    }

    @Override
    public ToolResult execute(ToolArguments args) {
        return switch (mode) {
            case NONE -> wrapped.execute(args);
            case CRASH -> throw new IllegalStateException("injected fault");
            case ERROR -> ToolResult.error(name() + " is unavailable (injected fault)");
        };
    }
}
