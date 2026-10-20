package tracewise.agent.tools;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;

/** Arguments already checked against the tool's schema, so required ones are present and typed. */
public final class ToolArguments {

    private final ObjectNode values;

    ToolArguments(ObjectNode values) {
        this.values = values;
    }

    public String string(String name) {
        return values.path(name).asText();
    }

    public int integer(String name, int defaultValue) {
        return values.has(name) ? values.get(name).asInt() : defaultValue;
    }

    public BigDecimal number(String name) {
        return values.get(name).decimalValue();
    }

    public boolean has(String name) {
        return values.has(name);
    }
}
