package tracewise.ingest;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import tracewise.domain.PaymentChannel;
import tracewise.domain.Transaction;

/**
 * SAML-D (Oztas et al., 2023): a UK bank's transactions over 10.5 months with normal behaviour designed to resemble
 * the suspicious kind, used to measure whether the agent can tell them apart.
 */
class SamlDCsvFormatTest {

    static final String HEADER = "Time,Date,Sender_account,Receiver_account,Amount,Payment_currency,Received_currency,"
            + "Sender_bank_location,Receiver_bank_location,Payment_type,Is_laundering,Laundering_type";

    final SamlDCsvFormat format = new SamlDCsvFormat(CurrencyConverter.bankOfCanadaDefaults());

    static List<String> row(String csv) {
        return Arrays.asList(csv.split(",", -1));
    }

    Transaction parse(String csv) {
        return format.parse(row(csv), 2);
    }

    @Test
    void recognisesItsHeaderOnly() {
        assertTrue(format.matches(row(HEADER)));
        assertFalse(format.matches(row(String.join(",", IbmCsvFormat.HEADER))));
    }

    @Test
    void aDomesticCardPaymentIsConvertedToCadWithAStableId() {
        var t = parse("10:35:19,2022-10-07,8724731955,2769355426,1000.00,UK pounds,UK pounds,UK,UK,Credit card,0,"
                + "Normal_Small_Fan_Out");

        assertEquals(Instant.parse("2022-10-07T10:35:19Z"), t.timestamp());
        assertEquals("8724731955", t.senderAccountId());
        assertEquals("2769355426", t.receiverAccountId());
        assertEquals(0, new BigDecimal("1000.00").compareTo(t.originalAmount()));
        assertEquals("GBP", t.currency());
        assertEquals(0, new BigDecimal("1881.00").compareTo(t.amountCad()), t.amountCad().toString());
        assertEquals(PaymentChannel.CARD, t.channel());
        assertNull(t.counterpartyCountry(), "the bank is in the UK, so a UK counterparty is domestic");
        assertEquals(Boolean.FALSE, t.launderingLabel());
        assertTrue(t.id().startsWith("SML-"));
        assertEquals(t.id(), parse("10:35:19,2022-10-07,8724731955,2769355426,1000.00,UK pounds,UK pounds,UK,UK,"
                + "Credit card,0,Normal_Small_Fan_Out").id(), "re-importing gives the same id");
    }

    @Test
    void aCrossBorderPaymentIsAWireWithTheForeignCountry() {
        var t = parse("10:35:20,2022-10-07,1491989064,8401255335,6019.64,UK pounds,Dirham,UK,UAE,Cross-border,1,"
                + "Layered_Fan_Out");

        assertEquals(PaymentChannel.WIRE, t.channel());
        assertEquals("AE", t.counterpartyCountry());
        assertTrue(t.isInternational());
        assertEquals(Boolean.TRUE, t.launderingLabel());
    }

    @Test
    void cashDepositsComeFromTheCashCounterAndWithdrawalsGoToIt() {
        var deposit = parse("10:46:37,2022-10-07,7401327478,4336451277,2603.30,UK pounds,UK pounds,UK,UK,Cash Deposit,1,"
                + "Smurfing");
        var withdrawal = parse("17:13:16,2022-10-07,7147448786,5557620276,114.20,UK pounds,UK pounds,UK,UK,"
                + "Cash Withdrawal,0,Normal_Cash_Withdrawal");

        assertEquals(Transaction.CASH, deposit.senderAccountId());
        assertEquals("4336451277", deposit.receiverAccountId());
        assertTrue(deposit.isCashDeposit());
        assertEquals("7147448786", withdrawal.senderAccountId());
        assertEquals(Transaction.CASH, withdrawal.receiverAccountId());
        assertEquals(PaymentChannel.CASH, withdrawal.channel());
    }

    @Test
    void theTypologyIsNeverKept() {
        var t = parse("10:35:19,2022-10-07,1,2,5.00,UK pounds,UK pounds,UK,UK,ACH,1,Structuring");
        assertFalse(t.toString().contains("Structuring"), "the typology names the answer");
        assertEquals(PaymentChannel.ACH, t.channel());
    }

    /**
     * Found in review 10: SAML-D's Amount is in pounds whatever the payment currency (its mean is about 10,000 for
     * every currency), so a yen or naira row is converted from GBP and never rejected for its currency.
     */
    @Test
    void theAmountIsInPoundsWhateverThePaymentCurrency() {
        var yen = parse("10:35:19,2022-10-07,1,2,1000.00,Yen,Yen,UK,Japan,Cross-border,0,Normal_Fan_Out");
        var naira = parse("10:35:19,2022-10-07,1,2,1000.00,Naira,Naira,Nigeria,UK,Cross-border,0,Normal_Fan_In");

        assertEquals("GBP", yen.currency());
        assertEquals(0, new BigDecimal("1881.00").compareTo(yen.amountCad()), yen.amountCad().toString());
        assertEquals("JP", yen.counterpartyCountry());
        assertEquals(0, new BigDecimal("1881.00").compareTo(naira.amountCad()));
        assertEquals("NG", naira.counterpartyCountry());
    }
}
