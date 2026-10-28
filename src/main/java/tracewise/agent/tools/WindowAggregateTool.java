package tracewise.agent.tools;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import tracewise.domain.Transaction;
import tracewise.llm.ToolParameter;
import tracewise.llm.ToolSchema;
import tracewise.persistence.Ledger;

/**
 * Read tool (the design's "aggregates over time windows"): an account's activity summed per day or week, so the
 * agent can see the shape of a busy account without paging through every row of account_history.
 */
public class WindowAggregateTool implements AgentTool {

    public static final String NAME = "window_aggregates";

    static final int MAX_WINDOWS = 60;

    private final Ledger ledger;

    public WindowAggregateTool(Ledger ledger) {
        this.ledger = ledger;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public ToolSchema schema() {
        return new ToolSchema(name(), "One account's activity summed per day or week (UTC): money in and out (count "
                + "and CAD total), distinct counterparties and channels. Use it for busy accounts before paging "
                + "account_history; cite transactions from account_history, which lists them. At most the "
                + MAX_WINDOWS + " most recent windows with activity.", List.of(
                new ToolParameter("account_id", ToolParameter.Type.STRING, "The account to summarise", true),
                new ToolParameter("window", ToolParameter.Type.STRING, "day or week (weeks start on Monday); default day",
                        false),
                new ToolParameter("from", ToolParameter.Type.STRING, "First day included, YYYY-MM-DD (default: no limit)", false),
                new ToolParameter("to", ToolParameter.Type.STRING, "Last day included, YYYY-MM-DD (default: no limit)", false)));
    }

    @Override
    public ToolResult execute(ToolArguments args) {
        String account = args.string("account_id");
        String window = args.has("window") ? args.string("window") : "day";
        if (!window.equals("day") && !window.equals("week")) {
            return ToolResult.error("'window' must be day or week, got '" + window + "'");
        }
        List<Transaction> all = ledger.transactionsFor(account, args.dateRange("from", "to"));
        if (all.isEmpty() && !Accounts.exists(ledger, account)) {
            return ToolResult.error("Unknown account '" + account + "'");
        }
        var windows = new TreeMap<LocalDate, Window>();
        for (Transaction t : all) {
            LocalDate day = LocalDate.ofInstant(t.timestamp(), ZoneOffset.UTC);
            LocalDate start = window.equals("day") ? day : day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            windows.computeIfAbsent(start, s -> new Window()).add(account, t);
        }
        var listed = windows.entrySet().stream().skip(Math.max(0, windows.size() - MAX_WINDOWS))
                .map(e -> e.getValue().row(e.getKey())).toList();
        var result = new LinkedHashMap<String, Object>();
        result.put("account_id", account);
        result.put("window", window);
        result.put("transaction_count", all.size());
        if (listed.size() < windows.size()) {
            result.put("note", "Showing the " + MAX_WINDOWS + " most recent of " + windows.size() + " windows");
        }
        result.put("windows", listed);
        return ToolResult.json(result);
    }

    /** One day's or week's totals; the laundering label is never read. */
    private static final class Window {
        int inCount;
        int outCount;
        BigDecimal in = BigDecimal.ZERO;
        BigDecimal out = BigDecimal.ZERO;
        final Set<String> counterparties = new HashSet<>();
        final Map<String, Integer> channels = new TreeMap<>();

        void add(String account, Transaction t) {
            boolean incoming = account.equals(t.receiverAccountId());
            if (incoming) {
                inCount++;
                in = in.add(t.amountCad());
            } else {
                outCount++;
                out = out.add(t.amountCad());
            }
            counterparties.add(incoming ? t.senderAccountId() : t.receiverAccountId());
            channels.merge(t.channel().name(), 1, Integer::sum);
        }

        Map<String, Object> row(LocalDate start) {
            var row = new LinkedHashMap<String, Object>();
            row.put("start", start.toString());
            row.put("in_count", inCount);
            row.put("in_cad", in);
            row.put("out_count", outCount);
            row.put("out_cad", out);
            row.put("counterparties", counterparties.size());
            row.put("channels", channels);
            return row;
        }
    }
}
