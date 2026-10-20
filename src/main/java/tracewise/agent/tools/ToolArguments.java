package tracewise.agent.tools;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import tracewise.domain.DateRange;

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

    /** Dates YYYY-MM-DD in UTC, both ends included; a missing end is open. */
    public DateRange dateRange(String fromName, String toName) {
        return new DateRange(has(fromName) ? date(fromName).atStartOfDay().toInstant(ZoneOffset.UTC) : DateRange.ALL.from(),
                has(toName) ? date(toName).plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC) : DateRange.ALL.to());
    }

    private LocalDate date(String name) {
        try {
            return LocalDate.parse(string(name));
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("'" + name + "' must be a date written YYYY-MM-DD, got '" + string(name) + "'");
        }
    }
}
