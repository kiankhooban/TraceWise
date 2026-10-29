package tracewise.monitoring;

import java.math.BigDecimal;
import java.util.List;
import tracewise.domain.Transaction;

final class Rules {

    private Rules() {
    }

    static BigDecimal total(List<Transaction> transactions) {
        return transactions.stream().map(Transaction::amountCad).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
