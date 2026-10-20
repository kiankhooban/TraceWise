package tracewise.persistence;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.JsonNodeFeature;

/** The one JSON setup for reading money: decimals stay exact BigDecimals with their scale, never double. */
public final class ExactJson {

    private ExactJson() {
    }

    /** A new mapper each call, so callers may add modules without affecting each other. */
    public static ObjectMapper mapper() {
        return new ObjectMapper().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .configure(JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES, false);
    }
}
