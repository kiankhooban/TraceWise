package tracewise.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One money movement. Cash deposits and withdrawals use the account id {@link #CASH} on the
 * counter side, so a deposit is CASH to customer and a withdrawal is customer to CASH.
 * launderingLabel is null when the source file has no label.
 */
public record Transaction(String id, Instant timestamp, String senderAccountId, String receiverAccountId,
        BigDecimal amountCad, BigDecimal originalAmount, String currency, PaymentChannel channel,
        String counterpartyCountry, Boolean launderingLabel) {

    public static final String CASH = "CASH";
    public static final String HOME_COUNTRY = "CA";

    public boolean isInternational() {
        return counterpartyCountry != null && !counterpartyCountry.isEmpty() && !HOME_COUNTRY.equals(counterpartyCountry);
    }

    public boolean isCashDeposit() {
        return channel == PaymentChannel.CASH && CASH.equals(senderAccountId);
    }
}
