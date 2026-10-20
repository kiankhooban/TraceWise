package tracewise.llm;

import java.math.BigDecimal;

/**
 * Which model to call and what it costs (USD per million tokens), so runs can report their cost.
 * provider is "anthropic" or "replay"; record saves every answer so the run can be replayed later.
 */
public record ModelConfig(String provider, String modelName, BigDecimal inputPricePerMTok,
        BigDecimal outputPricePerMTok, boolean record) {
}
