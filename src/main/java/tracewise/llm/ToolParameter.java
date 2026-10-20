package tracewise.llm;

public record ToolParameter(String name, Type type, String description, boolean required) {

    public enum Type {
        STRING, INTEGER, NUMBER, BOOLEAN
    }
}
