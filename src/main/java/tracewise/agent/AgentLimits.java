package tracewise.agent;

import java.math.BigDecimal;

/** Hard stops for one run: model calls, and spend in CAD. */
public record AgentLimits(int maxTurns, BigDecimal maxCostCad) {

    public static final AgentLimits DEFAULT = new AgentLimits(12, new BigDecimal("1.00"));
}
