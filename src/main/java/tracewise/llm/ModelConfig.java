package tracewise.llm;

import java.math.BigDecimal;

/** Which model to call and what it costs (USD per million tokens), so runs can report their cost. */
public record ModelConfig(String provider, String modelName, BigDecimal inputPricePerMTok,
        BigDecimal outputPricePerMTok) {
}
