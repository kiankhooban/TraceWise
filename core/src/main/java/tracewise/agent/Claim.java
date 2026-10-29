package tracewise.agent;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A transaction the agent cites as evidence: its id, an account in it, the CAD amount and the date (UTC). */
public record Claim(String transactionId, String accountId, BigDecimal amountCad, LocalDate date) {

    public String describe() {
        return "Transaction " + transactionId + ": " + amountCad.toPlainString() + " CAD on " + date + " involving " + accountId;
    }
}
