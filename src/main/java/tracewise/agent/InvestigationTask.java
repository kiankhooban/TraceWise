package tracewise.agent;

import static tracewise.agent.AgentJson.claims;
import static tracewise.agent.AgentJson.readJson;
import static tracewise.agent.AgentJson.requireArray;
import static tracewise.agent.AgentJson.requireText;
import static tracewise.agent.AgentJson.totals;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import tracewise.agent.tools.AccountHistoryTool;
import tracewise.agent.tools.WindowAggregateTool;
import tracewise.agent.tools.AddToWatchlistTool;
import tracewise.agent.tools.CounterpartiesTool;
import tracewise.agent.tools.CreateTaskTool;
import tracewise.agent.tools.LinkAlertsTool;
import tracewise.agent.tools.LinkedAlertsTool;
import tracewise.agent.tools.ProposeCloseTool;
import tracewise.agent.tools.ProposeEscalationTool;
import tracewise.agent.tools.SearchIndicatorsTool;
import tracewise.agent.tools.SetPriorityTool;
import tracewise.agent.tools.SimilarCasesTool;
import tracewise.domain.Alert;

/** F03: investigate one alert and answer with a structured finding. */
public class InvestigationTask implements AgentTask {

    /** What each rule looks for, without numbers: supervisors can change the thresholds. */
    private static final Map<String, String> RULES = Map.of(
            "R1", "large cash: cash deposits reaching the reporting threshold within 24 hours",
            "R2", "international transfer: transfers abroad reaching the reporting threshold within 24 hours",
            "R3", "structuring: repeated cash deposits just under the reporting threshold",
            "R4", "pass-through: money received and almost all of it sent onward within a short time",
            "R5", "fan-in: many different senders paying one account within a few days",
            "R6", "fan-out: one account paying many different recipients within a few days");

    static final String SYSTEM_PROMPT = """
            You are an anti-money-laundering investigator at a Canadian bank. Transaction monitoring rules \
            raised the alert you are given. Investigate it with the tools provided, then report a finding.

            Rules:
            - Every fact you state must come from a tool result in this conversation. Never invent \
            transactions, amounts, dates or accounts.
            - Cite evidence by transaction id exactly as a tool returned it, with its amount in CAD, its date \
            (YYYY-MM-DD, the UTC date of its timestamp) and an account involved in it. Cite at most 15 transactions, \
            the ones that matter most, and describe larger groups in the narrative (for example "38 payments to 12 \
            recipients"); a total lists at most 20 transaction ids.
            - Look beyond the flagged transactions: check the account's wider history and its counterparties, \
            both for further suspicious activity and for legitimate explanations such as payroll or regular \
            business income. For an account with many transactions, use window_aggregates to see its activity \
            per day or week first, then account_history for the dates that matter.
            - Use search_indicators to find the FINTRAC money-laundering indicators the evidence matches. Describe \
            the pattern in FINTRAC's own words, for example "deposits below the reporting threshold" or "funds \
            transferred in and out of an account within a short period", not in raw figures. List as indicators \
            only indicators search_indicators returned, word for word, that the evidence supports. If it finds \
            none, say so in the narrative instead of naming one.
            - similar_cases shows how past cases with a similar pattern were decided. Treat them as precedent for \
            your reasoning, never as evidence about this account.
            - If a tool returns an error, correct the arguments or continue with the evidence you have.
            - CASH is the cash counter (deposits and withdrawals). Account ids starting EXT- belong to other \
            institutions and cannot be looked up further.
            - The transaction data covers only the dates in data_covers. No activity before them says nothing about \
            when an account opened, a counterparty that only sends or only receives here may do more outside \
            them, and outflows larger than inflows can be paid from money held before the data starts: none of \
            these is a red flag.
            - ESCALATE when the evidence gives reasonable grounds to suspect money laundering, DISMISS when \
            there is a clear legitimate explanation, INCONCLUSIVE when the evidence is not enough to decide.

            Actions apply only to the case you are investigating and its alerts; each needs a justification \
            that cites the evidence:
            - set_priority, link_alerts, add_to_watchlist and create_task take effect at once. Use them only \
            when the evidence supports them, for example linking an alert on a counterparty in the same scheme.
            - propose_escalation and propose_close only propose: a human analyst approves or rejects them. \
            Before your final answer, propose escalation when your verdict is ESCALATE and propose closing when \
            it is DISMISS. Propose nothing when it is INCONCLUSIVE.

            Finish with your finding as one JSON object in a ```json block, with nothing after it:
            ```json
            {
              "verdict": "ESCALATE | DISMISS | INCONCLUSIVE",
              "confidence": 0.0,
              "indicators": ["each FINTRAC indicator from search_indicators that the evidence matches"],
              "cited_transactions": [
                {"transaction_id": "...", "account_id": "...", "amount_cad": 0.00, "date": "YYYY-MM-DD"}
              ],
              "totals": [
                {"description": "...", "amount_cad": 0.00, "transaction_ids": ["..."]}
              ],
              "narrative": "A short explanation an analyst can check against the cited transactions."
            }
            ```
            confidence is between 0 and 1. totals lists every sum stated in the narrative with the \
            transactions that add up to it, each id once. Only plain sums belong there: state differences, \
            averages and the absence of activity in the narrative instead. Use [] if there are none.""";

    private final Alert alert;
    private final String caseId;
    private final List<String> earlierDecisions;

    public InvestigationTask(Alert alert, String caseId) {
        this(alert, caseId, List.of());
    }

    /** earlierDecisions: what people decided about the agent's earlier proposals on this case, one line each. */
    public InvestigationTask(Alert alert, String caseId, List<String> earlierDecisions) {
        this.alert = alert;
        this.caseId = caseId;
        this.earlierDecisions = List.copyOf(earlierDecisions);
    }

    @Override
    public String systemPrompt() {
        return SYSTEM_PROMPT;
    }

    @Override
    public String initialMessage() {
        return "Investigate alert " + alert.id() + " (case " + caseId + ").\n"
                + "Rule: " + alert.ruleId() + " (" + RULES.getOrDefault(alert.ruleId(), "custom rule") + ")\n"
                + "Account: " + alert.accountId() + "\n"
                + "Flagged transactions: " + String.join(", ", alert.transactionIds()) + "\n"
                + "Total flagged: " + alert.totalCad().toPlainString() + " CAD\n"
                + "Risk score: " + alert.riskScore() + ", priority " + alert.priority() + " (1 high, 3 low)"
                + (earlierDecisions.isEmpty() ? "" : "\nEarlier decisions on this case (take the reasons into account):\n- "
                        + String.join("\n- ", earlierDecisions));
    }

    @Override
    public Set<String> toolNames() {
        return Set.of(AccountHistoryTool.NAME, WindowAggregateTool.NAME, CounterpartiesTool.NAME, LinkedAlertsTool.NAME, SearchIndicatorsTool.NAME,
                SimilarCasesTool.NAME, SetPriorityTool.NAME, LinkAlertsTool.NAME, AddToWatchlistTool.NAME, CreateTaskTool.NAME, ProposeCloseTool.NAME,
                ProposeEscalationTool.NAME);
    }

    @Override
    public String outputProblem(String finalText) {
        try {
            parseFinding(finalText, null);
            return null;
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
    }

    /**
     * Reads the finding from the agent's final answer; throws IllegalArgumentException naming what is wrong.
     * The finding is incomplete when the trace's run was cut short by a limit.
     */
    public AgentFinding parseFinding(String text, AgentTrace trace) {
        boolean incomplete = trace != null && trace.outcome() != null
                && trace.outcome().status() == OutcomeStatus.INCOMPLETE;
        JsonNode f = readJson(text);
        String verdict = requireText(f, "verdict");
        Verdict v;
        try {
            v = Verdict.valueOf(verdict);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("verdict must be ESCALATE, DISMISS or INCONCLUSIVE, not " + verdict);
        }
        JsonNode confidence = f.get("confidence");
        if (confidence == null || !confidence.isNumber() || confidence.asDouble() < 0 || confidence.asDouble() > 1) {
            throw new IllegalArgumentException("confidence must be a number between 0 and 1");
        }
        var indicators = new ArrayList<String>();
        for (JsonNode i : requireArray(f, "indicators")) {
            indicators.add(i.asText());
        }
        List<Claim> claims = claims(f);
        List<TotalClaim> totals = totals(f);
        return new AgentFinding(v, confidence.asDouble(), indicators, claims, totals, requireText(f, "narrative"),
                incomplete, trace);
    }
}
