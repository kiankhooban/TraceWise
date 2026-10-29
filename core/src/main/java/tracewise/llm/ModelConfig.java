package tracewise.llm;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Which model to call and what it costs (USD per million tokens), so runs can report their cost.
 * provider is "anthropic", "openai" (any OpenAI-compatible API) or "replay"; record saves every answer so the run can be replayed later.
 */
public record ModelConfig(String provider, String modelName, BigDecimal inputPricePerMTok,
        BigDecimal outputPricePerMTok, boolean record) {

    /** USD per million tokens (input, output), from the Anthropic price list (Haiku, Sonnet 2026-10-20; Opus as
     *  in the Stage 1 design, 2026-10-03). */
    private static final Map<String, BigDecimal[]> PRICES = Map.of(
            "claude-haiku-5-5", new BigDecimal[] {new BigDecimal("0.10"), new BigDecimal("0.50")},
            "claude-sonnet-5-5", new BigDecimal[] {new BigDecimal("2"), new BigDecimal("10")},
            "claude-opus-5-5", new BigDecimal[] {new BigDecimal("4"), new BigDecimal("20")});

    /** False when no price is known (an OpenAI-compatible model): runs then show no cost and no cost limit applies. */
    public boolean costKnown() {
        return inputPricePerMTok.signum() > 0 || outputPricePerMTok.signum() > 0;
    }

    /** The name runs are measured under (F12): a replay answers from recordings, so it is never the live model. */
    public String runLabel() {
        return "replay".equals(provider) ? modelName + " (replay)" : modelName;
    }

    /** A known model with its prices. Replay keeps the recorded model's prices so its cost is comparable. */
    public static ModelConfig of(String provider, String modelName, boolean record) {
        BigDecimal[] price = PRICES.get(modelName);
        // shortcut: an OpenAI-compatible model (live or replayed) has no published price, so its runs report no
        // cost and cost limits cannot stop them; add prices here if a paid provider is used
        boolean openAiModel = !modelName.startsWith("claude-")
                && ("openai".equalsIgnoreCase(provider) || "replay".equalsIgnoreCase(provider));
        if (price == null && openAiModel) {
            price = new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO};
        }
        if (price == null) {
            throw new IllegalArgumentException("No prices known for model " + modelName + "; known: " + PRICES.keySet());
        }
        return new ModelConfig(provider, modelName, price[0], price[1], record);
    }
}
