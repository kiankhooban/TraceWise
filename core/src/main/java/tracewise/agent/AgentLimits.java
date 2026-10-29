package tracewise.agent;

import java.math.BigDecimal;

/**
 * Limits for one run: model calls, and spend in CAD. Reaching either asks the model once more for its
 * best answer, so a run can exceed maxCostCad by that one final call.
 */
public record AgentLimits(int maxTurns, BigDecimal maxCostCad) {

    public static final AgentLimits DEFAULT = new AgentLimits(12, new BigDecimal("1.00"));
}
