package tracewise.agent.tools;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tracewise.domain.Transaction;
import tracewise.llm.ToolParameter;
import tracewise.llm.ToolSchema;
import tracewise.persistence.Ledger;

/** Read tool: an account's transactions in a date range, with money-in and money-out totals. */
public class AccountHistoryTool implements AgentTool {

    public static final String NAME = "account_history";

    static final int MAX_LISTED = 100;

    private final Ledger ledger;

    public AccountHistoryTool(Ledger ledger) {
        this.ledger = ledger;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public ToolSchema schema() {
        return new ToolSchema(name(), "Transactions sent or received by one account, oldest first, with totals in CAD. "
                + "Lists at most the " + MAX_LISTED + " most recent; totals cover the whole range.", List.of(
                new ToolParameter("account_id", ToolParameter.Type.STRING, "The account to look up", true),
                new ToolParameter("from", ToolParameter.Type.STRING, "First day included, YYYY-MM-DD (default: no limit)", false),
                new ToolParameter("to", ToolParameter.Type.STRING, "Last day included, YYYY-MM-DD (default: no limit)", false)));
    }

    @Override
    public ToolResult execute(ToolArguments args) {
        String account = args.string("account_id");
        List<Transaction> all = ledger.transactionsFor(account, args.dateRange("from", "to"));
        if (all.isEmpty() && !Accounts.exists(ledger, account)) {
            return ToolResult.error("Unknown account '" + account + "'");
        }
        BigDecimal in = BigDecimal.ZERO;
        BigDecimal out = BigDecimal.ZERO;
        var currencies = new java.util.TreeMap<String, Integer>();
        int selfTransfers = 0;
        for (Transaction t : all) {
            currencies.merge(t.currency(), 1, Integer::sum);
            if (t.senderAccountId().equals(t.receiverAccountId())) {
                selfTransfers++;
            }
            if (account.equals(t.receiverAccountId())) {
                in = in.add(t.amountCad());
            } else {
                out = out.add(t.amountCad());
            }
        }
        List<Transaction> listed = all.subList(Math.max(0, all.size() - MAX_LISTED), all.size());
        var result = new LinkedHashMap<String, Object>();
        result.put("account_id", account);
        result.put("data_covers", Accounts.coverage(ledger));
        result.put("transaction_count", all.size());
        result.put("total_in_cad", in);
        result.put("total_out_cad", out);
        result.put("currencies", currencies);
        result.put("self_transfers", selfTransfers);
        if (listed.size() < all.size()) {
            result.put("note", "Showing the " + MAX_LISTED + " most recent of " + all.size() + " transactions");
        }
        result.put("transactions", listed.stream().map(t -> row(account, t)).toList());
        return ToolResult.json(result);
    }

    /** The laundering label is ground truth for evaluation and is never shown to the agent. */
    private static Map<String, Object> row(String account, Transaction t) {
        boolean incoming = account.equals(t.receiverAccountId());
        var row = new LinkedHashMap<String, Object>();
        row.put("id", t.id());
        row.put("timestamp", t.timestamp().toString());
        row.put("direction", incoming ? "in" : "out");
        row.put("counterparty", incoming ? t.senderAccountId() : t.receiverAccountId());
        row.put("amount_cad", t.amountCad());
        if (!"CAD".equals(t.currency())) {
            row.put("original_amount", t.originalAmount() + " " + t.currency());
        }
        row.put("channel", t.channel().name());
        row.put("country", t.counterpartyCountry());
        return row;
    }
}
