package tracewise.llm;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Which model to call and what it costs (USD per million tokens), so runs can report their cost.
 * provider is "anthropic" or "replay"; record saves every answer so the run can be replayed later.
 */
public record ModelConfig(String provider, String modelName, BigDecimal inputPricePerMTok,
        BigDecimal outputPricePerMTok, boolean record) {

    /** USD per million tokens (input, output), checked against the Anthropic price list on 2026-10-20. */
    private static final Map<String, BigDecimal[]> PRICES = Map.of(
            "claude-haiku-5-5", new BigDecimal[] {new BigDecimal("0.10"), new BigDecimal("0.50")},
            "claude-sonnet-5-5", new BigDecimal[] {new BigDecimal("2"), new BigDecimal("10")});

    /** A known model with its prices. Replay keeps the recorded model's prices so its cost is comparable. */
    public static ModelConfig of(String provider, String modelName, boolean record) {
        BigDecimal[] price = PRICES.get(modelName);
        if (price == null) {
            throw new IllegalArgumentException("No prices known for model " + modelName + "; known: " + PRICES.keySet());
        }
        return new ModelConfig(provider, modelName, price[0], price[1], record);
    }
}
