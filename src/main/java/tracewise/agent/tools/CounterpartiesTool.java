package tracewise.agent.tools;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import tracewise.domain.Transaction;
import tracewise.llm.ToolParameter;
import tracewise.llm.ToolSchema;
import tracewise.persistence.Ledger;

/** Read tool: who an account sends money to and receives it from, with counts and totals per party. */
public class CounterpartiesTool implements AgentTool {

    private final Ledger ledger;

    public CounterpartiesTool(Ledger ledger) {
        this.ledger = ledger;
    }

    @Override
    public String name() {
        return "counterparties";
    }

    @Override
    public ToolSchema schema() {
        return new ToolSchema(name(), "Every party an account exchanged money with in a date range: counts and CAD totals "
                + "in each direction, largest total first. CASH is the cash counter; EXT- ids are outside the bank.", List.of(
                new ToolParameter("account_id", ToolParameter.Type.STRING, "The account to look up", true),
                new ToolParameter("from", ToolParameter.Type.STRING, "First day included, YYYY-MM-DD (default: no limit)", false),
                new ToolParameter("to", ToolParameter.Type.STRING, "Last day included, YYYY-MM-DD (default: no limit)", false)));
    }

    /** Running totals for one counterparty. */
    private static final class Party {
        final String id;
        int sentCount;
        int receivedCount;
        BigDecimal sent = BigDecimal.ZERO;
        BigDecimal received = BigDecimal.ZERO;
        final TreeSet<String> countries = new TreeSet<>();

        Party(String id) {
            this.id = id;
        }

        BigDecimal total() {
            return sent.add(received);
        }
    }

    @Override
    public ToolResult execute(ToolArguments args) {
        String account = args.string("account_id");
        List<Transaction> all = ledger.transactionsFor(account, args.dateRange("from", "to"));
        if (all.isEmpty() && !Accounts.exists(ledger, account)) {
            return ToolResult.error("Unknown account '" + account + "'");
        }
        var parties = new LinkedHashMap<String, Party>();
        for (Transaction t : all) {
            boolean incoming = account.equals(t.receiverAccountId());
            Party p = parties.computeIfAbsent(incoming ? t.senderAccountId() : t.receiverAccountId(), Party::new);
            if (incoming) {
                p.receivedCount++;
                p.received = p.received.add(t.amountCad());
            } else {
                p.sentCount++;
                p.sent = p.sent.add(t.amountCad());
            }
            if (t.counterpartyCountry() != null && !t.counterpartyCountry().isEmpty()) {
                p.countries.add(t.counterpartyCountry());
            }
        }
        var rows = new ArrayList<Map<String, Object>>();
        parties.values().stream().sorted(Comparator.comparing(Party::total).reversed()).forEach(p -> {
            var row = new LinkedHashMap<String, Object>();
            row.put("account_id", p.id);
            row.put("sent_count", p.sentCount);
            row.put("sent_total_cad", p.sent);
            row.put("received_count", p.receivedCount);
            row.put("received_total_cad", p.received);
            row.put("countries", List.copyOf(p.countries));
            rows.add(row);
        });
        return ToolResult.json(Map.of("account_id", account, "counterparties", rows));
    }
}
