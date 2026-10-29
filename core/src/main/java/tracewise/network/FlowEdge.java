package tracewise.network;

import java.math.BigDecimal;

/** F09: every transfer from one account to another in the range, summed. */
public record FlowEdge(String from, String to, BigDecimal totalCad, int transferCount) {
}
