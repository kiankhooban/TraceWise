package tracewise.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import tracewise.persistence.ExactJson;

/** Reading an agent's final JSON answer; every problem is an IllegalArgumentException naming what is wrong. */
final class AgentJson {

    private static final ObjectMapper JSON = ExactJson.mapper();

    private AgentJson() {
    }

    /** cited_transactions: each with transaction_id, account_id, amount_cad and date. */
    static List<Claim> claims(JsonNode answer) {
        var claims = new ArrayList<Claim>();
        for (JsonNode c : requireArray(answer, "cited_transactions")) {
            claims.add(new Claim(requireText(c, "transaction_id"), requireText(c, "account_id"),
                    requireAmount(c), requireDate(c)));
        }
        return claims;
    }

    /** totals (optional): each with description, amount_cad and transaction_ids. */
    static List<TotalClaim> totals(JsonNode answer) {
        var totals = new ArrayList<TotalClaim>();
        for (JsonNode t : answer.has("totals") ? requireArray(answer, "totals") : List.<JsonNode>of()) {
            var ids = new ArrayList<String>();
            requireArray(t, "transaction_ids").forEach(id -> ids.add(id.asText()));
            totals.add(new TotalClaim(requireText(t, "description"), requireAmount(t), ids));
        }
        return totals;
    }

    /** The ```json block if there is one, otherwise everything from the first { to the last }. */
    static JsonNode readJson(String text) {
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

    static String requireText(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || !v.isTextual() || v.asText().isBlank()) {
            throw new IllegalArgumentException("'" + field + "' must be a non-empty string");
        }
        return v.asText();
    }

    static JsonNode requireArray(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || !v.isArray()) {
            throw new IllegalArgumentException("'" + field + "' must be a list");
        }
        return v;
    }

    static BigDecimal requireAmount(JsonNode node) {
        JsonNode v = node.get("amount_cad");
        if (v == null || !v.isNumber()) {
            throw new IllegalArgumentException("'amount_cad' must be a number");
        }
        return v.decimalValue();
    }

    static LocalDate requireDate(JsonNode node) {
        String date = requireText(node, "date");
        try {
            return LocalDate.parse(date);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("'date' must be YYYY-MM-DD, not " + date);
        }
    }
}
