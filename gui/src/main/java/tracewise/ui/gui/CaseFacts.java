package tracewise.ui.gui;

import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import tracewise.domain.Alert;
import tracewise.domain.Transaction;

/** The case fact header: what the alert that opened the case flagged (after Hummingbird's case overview). */
record CaseFacts(String trigger, String subject, String window, String net, String risk, String riskStyle) {

    private static String net(BigDecimal in, BigDecimal out) {
        if (in.signum() == 0 && out.signum() == 0) {
            return "none";
        }
        String text = (in.signum() == 0 ? "" : Gui.cad(in) + " in") + (in.signum() != 0 && out.signum() != 0 ? "  " : "")
                + (out.signum() == 0 ? "" : Gui.cad(out) + " out");
        return text;
    }

    /** One flagged transaction from the subject's side: In when the money arrived, Out when it left. */
    record Flow(String direction, String id, String amount, String counterparty, String time) {
    }

    private static final DateTimeFormatter MINUTE = DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneOffset.UTC);

    /** The alert's flagged transactions in time order. */
    static List<Flow> flows(Alert alert, List<Transaction> accountTransactions) {
        Set<String> flagged = Set.copyOf(alert.transactionIds());
        return accountTransactions.stream().filter(t -> flagged.contains(t.id()))
                .sorted(java.util.Comparator.comparing(Transaction::timestamp)).map(t -> {
                    boolean in = t.receiverAccountId().equals(alert.accountId());
                    return new Flow(in ? "In" : "Out", t.id(), Gui.cad(t.amountCad()),
                            in ? t.senderAccountId() : t.receiverAccountId(), MINUTE.format(t.timestamp()));
                }).toList();
    }

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MM-dd").withZone(ZoneOffset.UTC);

    /** accountTransactions: the subject account's transactions; only the alert's flagged ones count. */
    static CaseFacts of(Alert alert, List<Transaction> accountTransactions) {
        Set<String> flagged = Set.copyOf(alert.transactionIds());
        var mine = accountTransactions.stream().filter(t -> flagged.contains(t.id())).toList();
        String window = mine.isEmpty() ? "not loaded"
                : DAY.format(mine.stream().map(Transaction::timestamp).min(java.util.Comparator.naturalOrder()).orElseThrow())
                        + " to " + DAY.format(mine.stream().map(Transaction::timestamp).max(java.util.Comparator.naturalOrder()).orElseThrow());
        BigDecimal in = mine.stream().filter(t -> t.receiverAccountId().equals(alert.accountId()))
                .map(Transaction::amountCad).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal out = mine.stream().filter(t -> t.senderAccountId().equals(alert.accountId()))
                .map(Transaction::amountCad).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CaseFacts(alert.ruleId() + " " + alert.id(), alert.accountId(), window,
                net(in, out), Display.riskLevel(alert.riskScore()) + " " + alert.riskScore(),
                Display.riskStyle(alert.riskScore()));
    }
}
