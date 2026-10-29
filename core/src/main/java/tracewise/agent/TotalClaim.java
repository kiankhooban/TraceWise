package tracewise.agent;

import java.math.BigDecimal;
import java.util.List;

/** A total the agent states, with the transactions it says add up to it. */
public record TotalClaim(String description, BigDecimal amountCad, List<String> transactionIds) {

    public TotalClaim {
        transactionIds = List.copyOf(transactionIds);
    }

    public String describe() {
        return description + ": " + amountCad.toPlainString() + " CAD from " + transactionIds;
    }
}
