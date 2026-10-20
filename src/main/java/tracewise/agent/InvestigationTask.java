package tracewise.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import tracewise.domain.Alert;
import tracewise.persistence.ExactJson;

/** F03: investigate one alert and answer with a structured finding. */
public class InvestigationTask implements AgentTask {

    private static final ObjectMapper JSON = ExactJson.mapper();

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
            (YYYY-MM-DD, the UTC date of its timestamp) and an account involved in it.
            - Look beyond the flagged transactions: check the account's wider history and its counterparties, \
            both for further suspicious activity and for legitimate explanations such as payroll or regular \
            business income.
            - If a tool returns an error, correct the arguments or continue with the evidence you have.
            - CASH is the cash counter (deposits and withdrawals). Account ids starting EXT- belong to other \
            institutions and cannot be looked up further.
            - ESCALATE when the evidence gives reasonable grounds to suspect money laundering, DISMISS when \
            there is a clear legitimate explanation, INCONCLUSIVE when the evidence is not enough to decide.

            Finish with your finding as one JSON object in a ```json block, with nothing after it:
            ```json
            {
              "verdict": "ESCALATE | DISMISS | INCONCLUSIVE",
              "confidence": 0.0,
              "indicators": ["each money-laundering indicator the evidence matches"],
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

    public InvestigationTask(Alert alert) {
        this.alert = alert;
    }

    @Override
    public String systemPrompt() {
        return SYSTEM_PROMPT;
    }

    @Override
    public String initialMessage() {
        return "Investigate alert " + alert.id() + ".\n"
                + "Rule: " + alert.ruleId() + " (" + RULES.getOrDefault(alert.ruleId(), "custom rule") + ")\n"
                + "Account: " + alert.accountId() + "\n"
                + "Flagged transactions: " + String.join(", ", alert.transactionIds()) + "\n"
                + "Total flagged: " + alert.totalCad().toPlainString() + " CAD\n"
                + "Risk score: " + alert.riskScore() + ", priority " + alert.priority() + " (1 high, 3 low)";
    }

    @Override
    public Set<String> toolNames() {
        return Set.of("account_history", "counterparties");
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
        var claims = new ArrayList<Claim>();
        for (JsonNode c : requireArray(f, "cited_transactions")) {
            claims.add(new Claim(requireText(c, "transaction_id"), requireText(c, "account_id"),
                    requireAmount(c), requireDate(c)));
        }
        var totals = new ArrayList<TotalClaim>();
        for (JsonNode t : f.has("totals") ? requireArray(f, "totals") : List.<JsonNode>of()) {
            var ids = new ArrayList<String>();
            requireArray(t, "transaction_ids").forEach(id -> ids.add(id.asText()));
            totals.add(new TotalClaim(requireText(t, "description"), requireAmount(t), ids));
        }
        return new AgentFinding(v, confidence.asDouble(), indicators, claims, totals, requireText(f, "narrative"),
                incomplete, trace);
    }

    /** The ```json block if there is one, otherwise everything from the first { to the last }. */
    private static JsonNode readJson(String text) {
        String body = text;
        int fence = text.indexOf("```json");
        if (fence >= 0) {
            int end = text.indexOf("```", fence + 7);
            body = text.substring(fence + 7, end < 0 ? text.length() : end);
        } else if (text.indexOf('{') >= 0 && text.lastIndexOf('}') > text.indexOf('{')) {
            body = text.substring(text.indexOf('{'), text.lastIndexOf('}') + 1);
        }
        try {
            JsonNode node = JSON.readTree(body);
            if (node != null && node.isObject()) {
                return node;
            }
        } catch (IOException e) {
            // reported below
        }
        throw new IllegalArgumentException("the finding must be one JSON object in a ```json block");
    }

    private static String requireText(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || !v.isTextual() || v.asText().isBlank()) {
            throw new IllegalArgumentException("'" + field + "' must be a non-empty string");
        }
        return v.asText();
    }

    private static JsonNode requireArray(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || !v.isArray()) {
            throw new IllegalArgumentException("'" + field + "' must be a list");
        }
        return v;
    }

    private static BigDecimal requireAmount(JsonNode node) {
        JsonNode v = node.get("amount_cad");
        if (v == null || !v.isNumber()) {
            throw new IllegalArgumentException("'amount_cad' must be a number");
        }
        return v.decimalValue();
    }

    private static LocalDate requireDate(JsonNode node) {
        String date = requireText(node, "date");
        try {
            return LocalDate.parse(date);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("'date' must be YYYY-MM-DD, not " + date);
        }
    }
}
